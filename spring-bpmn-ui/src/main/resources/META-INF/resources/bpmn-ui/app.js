/*
 * Single page UI of spring-bpmn on the JSON API of spring-bpmn-rest.
 *
 * Hash routes:
 *   #/definitions                                                   process definitions with instance counts
 *   #/instances?definition=&state=&sort=&direction=&page=           process instances (paged by the API)
 *   #/instances/{id}?state=&type=&sort=&direction=                  one instance with its diagram and steps
 *
 * The play button of a process definition asks for the variables in a dialog, starts an instance and shows it.
 * The Upload button (only if config.json says deploymentEnabled) deploys the BPMN file chosen in the file picker.
 * Filters and sorting are passed to the REST API as query parameters; the URL of the API comes from config.json.
 * The header links to the logout path of config.json; an empty path hides the link.
 * The headers of the columns the API can sort by are buttons that sort by them (see sortBy).
 * The diagram is rendered by the bpmn-js viewer (global BpmnJS), which keeps the bpmn.io watermark visible.
 * A document without diagram interchange (BPMN DI) is laid out by bpmn-auto-layout (import map of index.html).
 */
document.addEventListener('alpine:init', () => {
  // the bpmn-js viewer and the id of the instance it shows; kept out of the Alpine data, which would proxy them
  let viewer = null;
  let diagramOf = null;

  Alpine.data('bpmnUi', () => ({
    api: null,
    // the logout link of the header; empty hides it
    logoutPath: '',
    deploymentEnabled: false,
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

    async init() {
      try {
        const config = await this.get('config.json');
        this.api = config.apiBasePath;
        this.logoutPath = config.logoutPath || '';
        this.deploymentEnabled = config.deploymentEnabled === true;
      } catch (e) {
        this.error = 'Cannot load the UI configuration: ' + e.message;
        return;
      }
      window.addEventListener('hashchange', () => this.load());
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

    async loadInstance() {
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
      this.instance = instance;
      this.stepTypes = [...new Set(all.map((step) => step.type).filter((type) => type))].sort();
      this.steps = steps;
      await this.showDiagram(all);
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
          viewer.get('canvas').zoom('fit-viewport', 'auto');
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

    /** Marks the nodes with a running step as running, the other nodes with steps as finished. */
    markSteps(steps) {
      const canvas = viewer.get('canvas');
      const running = new Set(steps.filter((step) => step.state === 'running').map((step) => step.nodeId));
      const reached = new Set(steps.map((step) => step.nodeId));
      viewer.get('elementRegistry').forEach((element) => {
        canvas.removeMarker(element, 'step-running');
        canvas.removeMarker(element, 'step-finished');
        if (running.has(element.id)) {
          canvas.addMarker(element, 'step-running');
        } else if (reached.has(element.id)) {
          canvas.addMarker(element, 'step-finished');
        }
      });
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
     * Other errors reject with the detail of the problem response.
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
        throw new Error('Not authenticated: please log in.');
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
        throw new Error(response.status === 403 ? 'Not permitted: ' + detail : detail);
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

    formatJson(value) {
      return JSON.stringify(value, null, 2);
    },
  }));
});
