/*
 * Single page UI of spring-bpmn on the JSON API of spring-bpmn-rest.
 *
 * Hash routes:
 *   #/definitions                                                   process definitions with instance counts
 *   #/instances?definition=&state=&sort=&direction=&page=           process instances (paged by the API)
 *   #/instances/{id}?state=&type=&sort=&direction=                  one instance with its steps
 *
 * The play button of a process definition asks for the variables in a dialog, starts an instance and shows it.
 * Filters and sorting are passed to the REST API as query parameters; the URL of the API comes from config.json.
 */
document.addEventListener('alpine:init', () => {
  Alpine.data('bpmnUi', () => ({
    api: null,
    route: { page: 'definitions', id: null, query: {} },
    loading: false,
    error: null,
    definitions: [],
    instances: { items: [], page: 0, size: 0, total: 0 },
    instance: null,
    steps: [],
    stepTypes: [],
    start: { definition: null, variables: '{}', error: null, busy: false },

    async init() {
      try {
        this.api = (await this.get('config.json')).apiBasePath;
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
    },

    /** GETs JSON; rejects with the detail of a problem response. */
    async get(url) {
      const response = await fetch(url, { headers: { Accept: 'application/json' } });
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
