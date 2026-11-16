/*
 * Single page UI of spring-bpmn on the JSON API of spring-bpmn-rest.
 *
 * Hash routes:
 *   #/definitions                                                   process definitions with instance counts
 *   #/instances?definition=&state=&sort=&direction=&page=           process instances (paged by the API)
 *   #/instances/{id}?state=&type=&sort=&direction=                  one instance with its steps
 *
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
      return (await this.request(url)).json();
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
