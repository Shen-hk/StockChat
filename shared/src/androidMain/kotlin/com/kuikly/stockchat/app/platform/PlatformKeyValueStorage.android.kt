package com.kuikly.stockchat.app.platform

import com.kuikly.stockchat.data.storage.KeyValueStorage

internal actual fun platformKeyValueStorage(pagerId: String): KeyValueStorage = KuiklyKeyValueStorage(pagerId)
