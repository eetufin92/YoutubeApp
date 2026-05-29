(function() {
    // Check if we've already initialized to avoid double-injection crashes
    if (window._sb_initialized) return "Already Initialized";
    window._sb_initialized = true;

    // Use a stealth name that YouTube doesn't monitor
    const stealthName = '_sb_engine_internal';
    const sbPolyfill = {
        runtime: {
            sendMessage: function(message, callback) {
                const msgId = Math.random().toString(36).substring(7);
                if (callback) {
                    window._callbacks = window._callbacks || {};
                    window._callbacks[msgId] = callback;
                }
                if (window.AndroidBridge) {
                    window.AndroidBridge.postMessage(JSON.stringify({
                        type: 'runtime.sendMessage',
                        payload: message,
                        msgId: msgId
                    }));
                }
            },
            onMessage: {
                addListener: function(listener) {
                    window._messageListeners = window._messageListeners || [];
                    window._messageListeners.push(listener);
                }
            }
        },
        storage: {
            local: {
                get: function(keys, callback) {
                    const msgId = Math.random().toString(36).substring(7);
                    window._callbacks = window._callbacks || {};
                    window._callbacks[msgId] = callback;
                    if (window.AndroidBridge) {
                        window.AndroidBridge.postMessage(JSON.stringify({
                            type: 'storage.local.get',
                            keys: keys,
                            msgId: msgId
                        }));
                    }
                },
                set: function(items, callback) {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.postMessage(JSON.stringify({
                            type: 'storage.local.set',
                            items: items
                        }));
                    }
                    if (callback) callback();
                }
            }
        }
    };

    // Use Object.defineProperty to make it more "natural" to the JS engine
    try {
        Object.defineProperty(window, stealthName, {
            value: sbPolyfill,
            writable: true,
            configurable: true
        });
    } catch (e) {
        console.error('Polyfill: Failed to define stealth engine', e);
    }

    // Bridge for native to call back into JS
    window.onNativeMessage = function(json) {
        try {
            const data = JSON.parse(json);
            if (data.type === 'response' && data.msgId && window._callbacks && window._callbacks[data.msgId]) {
                window._callbacks[data.msgId](data.payload);
                delete window._callbacks[data.msgId];
            } else if (data.type === 'message' && window._messageListeners) {
                window._messageListeners.forEach(listener => listener(data.payload));
            }
        } catch (e) {
            console.error('Polyfill: Native message error', e);
        }
    };

    console.log('SponsorBlock Lite Polyfill Loaded');
    return "Lite Polyfill Loaded";
})();
