/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Absolute origin of the API, e.g. `https://algolens-api.onrender.com`.
   *
   * Leave unset for same-origin deployments: the dev server proxies `/api`, and the
   * single-container build has Spring Boot serve this bundle itself.
   */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
