package com.kuikly.stockchat.app.platform

import com.kuikly.stockchat.data.storage.KeyValueStorage

/**
 * Provides pager-scoped persistence on native runtimes and browser-scoped persistence on H5.
 * The browser cannot rely on the native SharedPreferences bridge, so its actual uses
 * localStorage to keep user-owned state (watchlist, settings and glossary progress) across routes.
 */
internal expect fun platformKeyValueStorage(pagerId: String): KeyValueStorage
