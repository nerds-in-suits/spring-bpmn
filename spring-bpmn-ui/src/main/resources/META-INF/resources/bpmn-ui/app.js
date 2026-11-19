/*
 * Single page UI of spring-bpmn on the JSON API of spring-bpmn-rest.
 *
 * Hash routes:
 *   #/definitions                                                   process definitions with instance counts
 *   #/instances?definition=&state=&sort=&direction=&page=           process instances (paged by the API)
 *   #/instances/{id}?state=&type=&sort=&direction=                  one instance with its diagram and steps
 *
 * The play button of a process definition asks for the variables in a dialog, starts an instance and shows it.
 * The variables of a running instance are edited in a table and saved with one PATCH (see saveVariables).
 * The Upload button (only if config.json says deploymentEnabled) deploys the BPMN file chosen in the file picker.
 * Filters and sorting are passed to the REST API as query parameters; the URL of the API comes from config.json.
 * The header links to the logout path of config.json; an empty path hides the link.
 * The headers of the columns the API can sort by are buttons that sort by them (see sortBy).
 * The diagram is rendered by the bpmn-js navigated viewer (global BpmnJS), which keeps the bpmn.io watermark visible.
 * In the diagram the mouse wheel scrolls (Shift+wheel horizontally), Ctrl+wheel zooms and dragging moves it;
 * the buttons over the diagram zoom in and out and fit it into the pane (see zoomDiagram and fitDiagram).
 * A document without diagram interchange (BPMN DI) is laid out by bpmn-auto-layout (import map of index.html).
 * The detail page of a running instance reloads its data every refreshInterval of config.json (see refreshInstance).
 * A pending step whose execution failed is shown as failed, in red in the diagram, with its error in the steps table
 * (see stepState and markSteps).
 */
document.addEventListener('alpine:init', () => {
  // the bpmn-js viewer and the id of the instance it shows; kept out of the Alpine data, which would proxy them
  let viewer = null;
  let diagramOf = null;
  // the last key of a row of the variables table
  let rowKey = 0;
  // the margin in screen pixels between the fitted diagram and the border of its pane
  const DIAGRAM_PADDING = 24;
  // the pending timer of the auto-refresh, and whether a refresh is in flight
  let refreshTimer = null;
  let refreshing = false;
  // counts the loads of a route; a load overtaken by a navigation discards its data
  let loads = 0;
  // counts the saves of the variables; a load that overlaps a save keeps the saved variables
  let variableSaves = 0;

  Alpine.data('bpmnUi', () => ({
    api: null,
    // the logout link of the header; empty hides it
    logoutPath: '',
    deploymentEnabled: false,
    // the interval of the auto-refresh of a running instance in milliseconds; 0 disables it
    refreshInterval: 0,
    // the auto-refresh of the shown instance: active while it is running, the time of the last load, and stopped
    // after the API answered 401 or 403
    refresh: { active: false, updated: null, stopped: false },
    route: { page: 'definitions', id: null, query: {} },
    loading: false,
    error: null,
    definitions: [],
    instances: { items: [], page: 0, size: 0, total: 0 },
    instance: null,
    steps: [],
    stepTypes: [],
    diagram: { shown: false, message: null },
    start: { definition: null, variables: '{}', error: null, busy: false },
    upload: { message: null, error: null, busy: false },
    // the variables table of the instance: rows of { key, name, value (JSON text), original name or null if new },
    // and the original names of the removed rows
    variables: { rows: [], removed: [], message: null, error: null, busy: false },

    async init() {
      try {
        const config = await this.get('config.json');
        this.api = config.apiBasePath;
        this.logoutPath = config.logoutPath || '';
        this.deploymentEnabled = config.deploymentEnabled === true;
        this.refreshInterval = Math.max(0, Number(config.refreshInterval) || 0);
      } catch (e) {
        this.error = 'Cannot load the UI configuration: ' + e.message;
        return;
      }
      window.addEventListener('hashchange', () => this.load());
      document.addEventListener('visibilitychange', () => this.visibilityChanged());
      await this.load();
    },

    /** Parses location.hash into route. */
    parseRoute() {
      const hash = window.location.hash.replace(/^#\/?/, '');
      const [path, search] = hash.split('?');
      const parts = path.split('/').filter((part) => part !== '');
      const query = Object.fromEntries(new URLSearchParams(search || ''));
      if (parts[0] === 'instances' && parts[1]) {
        return { page: 'instance', id: decodeURIComponent(parts[1]), query };
      }
      if (parts[0] === 'instances') {
        return { page: 'instances', id: null, query };
      }
      return { page: 'definitions', id: null, query };
    },

    /** Loads the data of the current route. */
    async load() {
      loads++;
      this.cancelRefresh();
      this.refresh = { active: false, updated: null, stopped: false };
      this.route = this.parseRoute();
      this.error = null;
      this.upload = { message: null, error: null, busy: false };
      this.loading = true;
      try {
        if (this.route.page === 'definitions') {
          this.definitions = await this.get(this.api + '/process-definitions');
        } else if (this.route.page === 'instances') {
          await this.loadInstances();
        } else {
          await this.loadInstance();
        }
      } catch (e) {
        this.error = e.message;
      } finally {
        this.loading = false;
        this.scheduleRefresh();
      }
    },

    async loadInstances() {
      const [definitions, instances] = await Promise.all([
        this.get(this.api + '/process-definitions'),
        this.get(this.api + '/process-instances' + this.search(this.route.query,
          ['definition', 'state', 'sort', 'direction', 'page'])),
      ]);
      this.definitions = definitions;
      this.instances = instances;
    },

    /**
     * Loads the instance of the route with its steps and shows them in the diagram. Data that arrives after a
     * navigation to another route is discarded. On a reload of the same instance (a changed step filter or the
     * auto-refresh) the variables table keeps its unsaved edits, and their base, the stored variables they are
     * compared with, so a save changes only what the user edited; a save in progress or done meanwhile keeps the
     * variables it saved. Unchanged variables do not reset the table, so its message stays.
     */
    async loadInstance() {
      const load = loads;
      const saves = variableSaves;
      const base = this.api + '/process-instances/' + encodeURIComponent(this.route.id);
      if (this.instance && this.instance.id !== this.route.id) {
        this.instance = null;
        this.steps = [];
      }
      const [instance, all, steps] = await Promise.all([
        this.get(base),
        this.get(base + '/steps'),
        this.get(base + '/steps' + this.search(this.route.query, ['state', 'type', 'sort', 'direction'])),
      ]);
      if (load !== loads) {
        return;
      }
      const same = this.instance !== null && this.instance.id === instance.id;
      if (same && (this.variables.busy || saves !== variableSaves || this.variablesChanged())) {
        instance.variables = this.instance.variables;
      } else if (!same || JSON.stringify(instance.variables) !== JSON.stringify(this.instance.variables)) {
        this.resetVariables(instance.variables);
      }
      this.instance = instance;
      this.stepTypes = [...new Set(all.map((step) => step.type).filter((type) => type))].sort();
      this.steps = steps;
      await this.showDiagram(all);
      this.refresh.updated = new Date();
    },

    /**
     * Reloads the shown instance (see loadInstance) without the loading indicator, then schedules the next refresh
     * while the instance is running; the refresh that finds it ended shows the end state and is the last one.
     * Skipped while a load or another refresh is in flight, which schedule the next refresh themselves.
     * The diagram is not imported or fitted again, only its markers change, so zoom and scroll stay.
     * A failure shows the error like other load errors and tries again after the interval, except 401 and 403,
     * which stop the auto-refresh until the next navigation, so an expired login or missing permission does not
     * repeat the request.
     */
    async refreshInstance() {
      refreshTimer = null;
      if (refreshing || this.loading || this.route.page !== 'instance') {
        return;
      }
      refreshing = true;
      const load = loads;
      try {
        await this.loadInstance();
        if (load === loads) {
          this.error = null;
        }
      } catch (e) {
        if (load === loads) {
          this.error = 'Cannot refresh the process instance: ' + e.message;
          this.refresh.stopped = e.status === 401 || e.status === 403;
        }
      } finally {
        refreshing = false;
        this.scheduleRefresh();
      }
    },

    /**
     * Schedules the next auto-refresh if it is enabled and not stopped, the detail page of a running instance is
     * shown and the browser tab is visible (see visibilityChanged).
     */
    scheduleRefresh() {
      this.cancelRefresh();
      this.refresh.active = this.refreshInterval > 0 && !this.refresh.stopped && this.route.page === 'instance'
        && this.instance !== null && this.instance.id === this.route.id && this.instance.state === 'running';
      if (this.refresh.active && document.visibilityState === 'visible') {
        refreshTimer = setTimeout(() => this.refreshInstance(), this.refreshInterval);
      }
    },

    /** Cancels the pending auto-refresh. */
    cancelRefresh() {
      if (refreshTimer !== null) {
        clearTimeout(refreshTimer);
        refreshTimer = null;
      }
    },

    /** Pauses the auto-refresh while the browser tab is hidden and refreshes at once when it is visible again. */
    visibilityChanged() {
      if (document.visibilityState !== 'visible') {
        this.cancelRefresh();
      } else if (this.refresh.active) {
        this.refreshInstance();
      }
    },

    /** @return the hint of the active auto-refresh: its interval and the time of the last load */
    refreshHint() {
      const updated = this.refresh.updated ? ', last updated ' + this.refresh.updated.toLocaleTimeString() : '';
      return 'Auto-refresh every ' + this.refreshInterval / 1000 + ' s' + updated;
    },

    /**
     * Shows the diagram of the definition version the instance was started from (loaded once per instance) and marks
     * the nodes of its steps. A document without diagram interchange (BPMN DI) is laid out in the browser first (see
     * layout) and the page says so; the stored definition is not changed. A failure shows a message instead.
     */
    async showDiagram(steps) {
      const id = this.route.id;
      try {
        if (diagramOf !== id) {
          diagramOf = null;
          this.diagram = { shown: true, message: null };
          await this.$nextTick();
          if (!viewer) {
            viewer = new BpmnJS({ container: this.$refs.diagram });
          }
          let xml = await this.getXml(this.api + '/process-instances/' + encodeURIComponent(id) + '/bpmn');
          const generated = !this.hasDiagram(xml);
          if (generated) {
            xml = await this.layout(xml);
          }
          if (this.route.id !== id) {
            return;
          }
          await viewer.importXML(xml);
          this.diagram.message = generated
            ? 'The process definition has no diagram layout (BPMN DI); the layout was generated automatically.'
            : null;
          await this.openDiagramOf(this.instance.processDefinition);
          this.fitDiagram();
          diagramOf = id;
        }
        if (this.diagram.shown) {
          this.markSteps(steps);
        }
      } catch (e) {
        // not retried on every filter change of the same instance
        diagramOf = id;
        if (viewer) {
          viewer.clear();
        }
        this.diagram = { shown: false, message: 'Cannot show the diagram: ' + e.message };
      }
    },

    /** @return whether the BPMN document xml has a diagram (bpmndi:BPMNDiagram in any namespace prefix) */
    hasDiagram(xml) {
      const document = new DOMParser().parseFromString(xml, 'application/xml');
      return document.getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/DI', 'BPMNDiagram').length > 0;
    },

    /**
     * Generates the diagram interchange of the BPMN document xml with bpmn-auto-layout 2.x, loaded on first use
     * through the import map of index.html (sub-processes are drawn collapsed with a drill-down plane).
     * @return the document with the generated diagram
     */
    async layout(xml) {
      const { layoutProcess } = await import('bpmn-auto-layout');
      return (await layoutProcess(xml)).xml;
    },

    /** Opens the diagram of the process named name, if the document has several diagrams. */
    async openDiagramOf(name) {
      const diagrams = viewer.getDefinitions().diagrams || [];
      const shows = (element) => Boolean(element) && ((element.name || element.id) === name
        || (element.participants || []).some((participant) => shows(participant.processRef)));
      const diagram = diagrams.find((candidate) => candidate.plane && shows(candidate.plane.bpmnElement));
      if (diagram && diagram !== diagrams[0]) {
        await viewer.open(diagram);
      }
    },

    /**
     * Fits the whole diagram into the pane and centres it, with a margin of DIAGRAM_PADDING screen pixels so that no
     * element touches the border; a diagram that fits at its natural size is centred without being enlarged.
     */
    fitDiagram() {
      if (!viewer) {
        return;
      }
      const canvas = viewer.get('canvas');
      const { inner, outer } = canvas.viewbox();
      if (!inner.width || !inner.height) {
        return;
      }
      const scale = Math.min(1, (outer.width - 2 * DIAGRAM_PADDING) / inner.width,
        (outer.height - 2 * DIAGRAM_PADDING) / inner.height);
      const width = outer.width / scale;
      const height = outer.height / scale;
      canvas.viewbox({
        x: inner.x + (inner.width - width) / 2,
        y: inner.y + (inner.height - height) / 2,
        width,
        height,
      });
    },

    /** Zooms the diagram one step in (direction 1) or out (direction -1) around the centre of the pane. */
    zoomDiagram(direction) {
      if (viewer) {
        viewer.get('zoomScroll').stepZoom(direction);
      }
    },

    /**
     * Marks the nodes with a failed step (see stepState) as failed, the other nodes with a running step as running and
     * the remaining nodes with steps as finished.
     */
    markSteps(steps) {
      const canvas = viewer.get('canvas');
      const failed = new Set(steps.filter((step) => this.stepState(step) === 'failed').map((step) => step.nodeId));
      const running = new Set(steps.filter((step) => step.state === 'running').map((step) => step.nodeId));
      const reached = new Set(steps.map((step) => step.nodeId));
      viewer.get('elementRegistry').forEach((element) => {
        canvas.removeMarker(element, 'step-failed');
        canvas.removeMarker(element, 'step-running');
        canvas.removeMarker(element, 'step-finished');
        if (failed.has(element.id)) {
          canvas.addMarker(element, 'step-failed');
        } else if (running.has(element.id)) {
          canvas.addMarker(element, 'step-running');
        } else if (reached.has(element.id)) {
          canvas.addMarker(element, 'step-finished');
        }
      });
    },

    /**
     * @return the state shown for step: failed if it is pending (running) and its execution failed, so it waits for a
     *         retry or a fix; else its state, running or finished. A finished step keeps the error of an earlier
     *         failed attempt, which the steps table shows, but is not failed.
     */
    stepState(step) {
      return step.state === 'running' && step.errorDate ? 'failed' : step.state;
    },

    /** GETs JSON; rejects with the detail of a problem response. */
    async get(url) {
      return (await this.request(url)).json();
    },

    /** GETs XML as text; rejects with the detail of a problem response. */
    async getXml(url) {
      return (await this.request(url, { headers: { Accept: 'application/xml, application/problem+json' } })).text();
    },

    /**
     * fetch() with the session cookie for all requests of the UI; resolves with the response if it is ok.
     * Modifying requests send the CSRF token of the cookie XSRF-TOKEN in the header X-XSRF-TOKEN.
     * On 401 the page is reloaded once, so the login of the application takes over; 403 rejects with "Not permitted".
     * Other errors reject with the detail of the problem response. The errors carry the HTTP status as status.
     */
    async request(url, options = {}) {
      const method = (options.method || 'GET').toUpperCase();
      const headers = { Accept: 'application/json', ...options.headers };
      const token = document.cookie.split('; ').find((cookie) => cookie.startsWith('XSRF-TOKEN='));
      if (token && method !== 'GET' && method !== 'HEAD') {
        headers['X-XSRF-TOKEN'] = decodeURIComponent(token.substring('XSRF-TOKEN='.length));
      }
      const response = await fetch(url, { ...options, method, headers });
      if (response.status === 401) {
        if (!sessionStorage.getItem('bpmnUiReloaded')) {
          sessionStorage.setItem('bpmnUiReloaded', 'true');
          window.location.reload();
        }
        throw Object.assign(new Error('Not authenticated: please log in.'), { status: response.status });
      }
      sessionStorage.removeItem('bpmnUiReloaded');
      if (!response.ok) {
        let detail = response.status + ' ' + response.statusText;
        try {
          const problem = await response.json();
          detail = problem.detail || problem.title || detail;
        } catch (ignored) {
          // no problem details
        }
        throw Object.assign(new Error(response.status === 403 ? 'Not permitted: ' + detail : detail),
          { status: response.status });
      }
      return response;
    },

    /** POSTs body as JSON and returns the JSON response; rejects with the detail of a problem response. */
    async post(url, body) {
      return (await this.request(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })).json();
    },

    /** PATCHes body as JSON Merge Patch and returns the JSON response; rejects with the detail of a problem response. */
    async patch(url, body) {
      return (await this.request(url, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/merge-patch+json' },
        body: JSON.stringify(body),
      })).json();
    },

    /** POSTs file as application/xml and returns the JSON response; rejects with the detail of a problem response. */
    async postXml(url, file) {
      return (await this.request(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/xml' },
        body: file,
      })).json();
    },

    /**
     * Deploys the BPMN file chosen in the file input, then reloads the definitions and names the deployed ones;
     * shows the detail of a problem response if the API rejects it (e.g. 403 if deployment is disabled).
     */
    async uploadDefinition(input) {
      const file = input.files[0];
      // reset, so choosing the same file again fires another change event
      input.value = '';
      if (!file) {
        return;
      }
      this.upload = { message: null, error: null, busy: true };
      try {
        const deployed = await this.postXml(this.api + '/process-definitions', file);
        const names = (state) => deployed.filter((definition) => definition.registered === state)
          .map((definition) => definition.name).join(', ');
        const changed = names(true);
        const unchanged = names(false);
        this.upload.message = 'Uploaded ' + file.name + '.'
          + (changed ? ' Deployed: ' + changed + '.' : '')
          + (unchanged ? ' Unchanged: ' + unchanged + '.' : '');
      } catch (e) {
        this.upload.error = 'Cannot upload ' + file.name + ': ' + e.message;
        return;
      } finally {
        this.upload.busy = false;
      }
      try {
        this.definitions = await this.get(this.api + '/process-definitions');
      } catch (e) {
        this.error = e.message;
      }
    },

    /** Opens the dialog that asks for the variables to start an instance of the process definition. */
    openStart(definition) {
      this.start = { definition, variables: '{}', error: null, busy: false };
      this.$refs.startDialog.showModal();
    },

    /**
     * Starts an instance with the variables of the dialog, which must be a JSON object, and shows it;
     * keeps the dialog open with the error if the variables are invalid or the API rejects the start.
     */
    async confirmStart() {
      let variables;
      try {
        variables = JSON.parse(this.start.variables.trim() || '{}');
      } catch (e) {
        this.start.error = 'Variables are not valid JSON: ' + e.message;
        return;
      }
      if (variables === null || typeof variables !== 'object' || Array.isArray(variables)) {
        this.start.error = 'Variables must be a JSON object, e.g. {"amount": 500}.';
        return;
      }
      this.start.error = null;
      this.start.busy = true;
      try {
        const instance = await this.post(this.api + '/process-instances',
          { processDefinition: this.start.definition, variables });
        this.$refs.startDialog.close();
        window.location.hash = this.instanceLink(instance.id);
      } catch (e) {
        this.start.error = e.message;
      } finally {
        this.start.busy = false;
      }
    },

    /** Fills the variables table with the variables of the instance, sorted by name, and discards the edits. */
    resetVariables(variables) {
      const rows = Object.keys(variables || {}).sort()
        .map((name) => ({ key: ++rowKey, name, value: JSON.stringify(variables[name]), original: name }));
      this.variables = { rows, removed: [], message: null, error: null, busy: false };
    },

    /** Adds an empty row to the variables table. */
    addVariable() {
      this.variables.rows.push({ key: ++rowKey, name: '', value: '', original: null });
      this.variables.message = null;
    },

    /** Removes the row at index from the variables table; a stored variable is removed on save. */
    removeVariable(index) {
      const [row] = this.variables.rows.splice(index, 1);
      if (row.original !== null) {
        this.variables.removed.push(row.original);
      }
      this.variables.message = null;
    },

    /**
     * Parses the text of a value: valid JSON (a number, true, an object, an array, a "quoted string") is taken as
     * typed, any other text as a string, so plain text needs no quotes.
     */
    parseValue(text) {
      try {
        return JSON.parse(text);
      } catch (e) {
        return text;
      }
    },

    /**
     * @return the JSON Merge Patch of the edits of the variables table: null for removed and renamed variables, the
     *         parsed value for new, renamed and changed rows; empty if nothing changed
     */
    variablesPatch() {
      const stored = this.instance ? this.instance.variables || {} : {};
      const patch = {};
      this.variables.removed.forEach((name) => {
        patch[name] = null;
      });
      this.variables.rows.filter((row) => row.original !== null && row.original !== row.name.trim())
        .forEach((row) => {
          patch[row.original] = null;
        });
      this.variables.rows.forEach((row) => {
        const name = row.name.trim();
        if (row.original !== name || row.value !== JSON.stringify(stored[row.original])) {
          patch[name] = this.parseValue(row.value);
        }
      });
      return patch;
    },

    /** @return whether the variables table has unsaved edits */
    variablesChanged() {
      return Object.keys(this.variablesPatch()).length > 0;
    },

    /**
     * Saves the edits of the variables table with one PATCH (JSON Merge Patch, so all edits are stored in one
     * transaction) and shows the resulting variables. Keeps the edits with the error if a name is empty or repeated,
     * a value is null, or the API rejects the change (e.g. 403 without the authority update-variables, 422 for an
     * ended instance).
     */
    async saveVariables() {
      const names = this.variables.rows.map((row) => row.name.trim());
      if (names.includes('')) {
        this.variables.error = 'Every variable needs a name.';
        return;
      }
      const repeated = names.find((name, index) => names.indexOf(name) !== index);
      if (repeated !== undefined) {
        this.variables.error = 'The variable ' + repeated + ' is listed twice.';
        return;
      }
      const empty = this.variables.rows.find((row) => this.parseValue(row.value) === null);
      if (empty) {
        this.variables.error = 'The variable ' + empty.name.trim() + ' cannot be null; remove it with the bin.';
        return;
      }
      const patch = this.variablesPatch();
      this.variables.error = null;
      if (Object.keys(patch).length === 0) {
        this.variables.message = 'No changes to save.';
        return;
      }
      this.variables.message = null;
      this.variables.busy = true;
      variableSaves++;
      try {
        const variables = await this.patch(this.api + '/process-instances/' + encodeURIComponent(this.instance.id)
          + '/variables', patch);
        this.instance.variables = variables;
        this.resetVariables(variables);
        this.variables.message = 'Variables saved.';
      } catch (e) {
        this.variables.error = 'Cannot save the variables: ' + e.message;
      } finally {
        this.variables.busy = false;
      }
    },

    /** @return the query string of the non-empty parameters of query named in names */
    search(query, names) {
      const params = new URLSearchParams();
      names.filter((name) => query[name]).forEach((name) => params.set(name, query[name]));
      const text = params.toString();
      return text ? '?' + text : '';
    },

    /** Navigates to the current page with changed query parameters; empty values are removed. */
    navigate(changes) {
      const query = { ...this.route.query, ...changes };
      const path = this.route.page === 'instance'
        ? '#/instances/' + encodeURIComponent(this.route.id)
        : '#/' + this.route.page;
      window.location.hash = path + this.search(query, Object.keys(query));
    },

    /** Changes a filter or sort parameter and goes back to the first page. */
    filter(name, value) {
      this.navigate({ [name]: value, page: '' });
    },

    /**
     * Sorts by column, a sort value of the API, on a click on its header and goes back to the first page:
     * the sorted column again reverses the direction, another column is sorted ascending.
     * Without sort and direction in the URL the API sorts by start date ascending, so that column shows as sorted.
     */
    sortBy(column) {
      const direction = this.sortColumn() === column && this.sortDirection() === 'asc' ? 'desc' : 'asc';
      this.navigate({ sort: column, direction, page: '' });
    },

    /** @return the sorted column, the default of the API if the URL has none */
    sortColumn() {
      return this.route.query.sort || 'startDate';
    },

    /** @return the sort direction, asc or desc, the default of the API if the URL has none */
    sortDirection() {
      return this.route.query.direction === 'desc' ? 'desc' : 'asc';
    },

    /** @return the aria-sort value of the header of column; null removes it from the unsorted columns */
    ariaSort(column) {
      if (this.sortColumn() !== column) {
        return null;
      }
      return this.sortDirection() === 'desc' ? 'descending' : 'ascending';
    },

    /** @return the arrow shown in the header of column: up ascending, down descending, none if not sorted */
    sortMark(column) {
      if (this.sortColumn() !== column) {
        return '';
      }
      return this.sortDirection() === 'desc' ? ' ▼' : ' ▲';
    },

    instancesLink(definition, state) {
      return '#/instances' + this.search({ definition, state }, ['definition', 'state']);
    },

    instanceLink(id) {
      return '#/instances/' + encodeURIComponent(id);
    },

    get lastPage() {
      return Math.max(0, Math.ceil(this.instances.total / this.instances.size) - 1);
    },

    formatDate(value) {
      return value ? new Date(value).toLocaleString() : '';
    },
  }));
});
