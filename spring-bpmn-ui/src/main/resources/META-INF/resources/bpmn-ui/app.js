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
 * The diagram is rendered by the bpmn-js viewer (global BpmnJS), which keeps the bpmn.io watermark visible.
 */
document.addEventListener('alpine:init', () => {
  // the bpmn-js viewer and the id of the instance it shows; kept out of the Alpine data, which would proxy them
  let viewer = null;
  let diagramOf = null;

  Alpine.data('bpmnUi', () => ({
    api: null,
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
     * the nodes of its steps. A document without diagram interchange (BPMN DI) or a failure shows a message instead.
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
          const xml = await this.getXml(this.api + '/process-instances/' + encodeURIComponent(id) + '/bpmn');
          if (this.route.id !== id) {
            return;
          }
          await viewer.importXML(xml);
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
        this.diagram = {
          shown: false,
          message: /no diagram to display/.test(e.message)
            ? 'The process definition has no diagram layout (BPMN DI).'
            : 'Cannot show the diagram: ' + e.message,
        };
      }
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
      return (await this.fetchOk(url, 'application/json')).json();
    },

    /** GETs XML as text; rejects with the detail of a problem response. */
    async getXml(url) {
      return (await this.fetchOk(url, 'application/xml, application/problem+json')).text();
    },

    /** GETs url; rejects with the detail of a problem response. */
    async fetchOk(url, accept) {
      const response = await fetch(url, { headers: { Accept: accept } });
      if (!response.ok) {
        let detail = response.status + ' ' + response.statusText;
        try {
          const problem = await response.json();
          detail = problem.detail || problem.title || detail;
        } catch (ignored) {
          // no problem details
        }
        throw new Error(detail);
      }
      return response;
    },

    /** POSTs body as JSON and returns the JSON response; rejects with the detail of a problem response. */
    async post(url, body) {
      const response = await fetch(url, {
        method: 'POST',
        headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!response.ok) {
        let detail = response.status + ' ' + response.statusText;
        try {
          const problem = await response.json();
          detail = problem.detail || problem.title || detail;
        } catch (ignored) {
          // no problem details
        }
        throw new Error(detail);
      }
      return response.json();
    },

    /** POSTs file as application/xml and returns the JSON response; rejects with the detail of a problem response. */
    async postXml(url, file) {
      const response = await fetch(url, {
        method: 'POST',
        headers: { Accept: 'application/json', 'Content-Type': 'application/xml' },
        body: file,
      });
      if (!response.ok) {
        let detail = response.status + ' ' + response.statusText;
        try {
          const problem = await response.json();
          detail = problem.detail || problem.title || detail;
        } catch (ignored) {
          // no problem details
        }
        throw new Error(detail);
      }
      return response.json();
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
