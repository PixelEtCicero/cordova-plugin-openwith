function initOpenwithPlugin (root) {
    'use strict'

    // imports
    // var cordova = require('cordova')
    var PLUGIN_NAME = 'OpenWithPlugin'

    // the returned object
    var openwith = {}

    //
    // exported constants
    //

    // actions
    openwith.SEND = 'SEND'
    openwith.VIEW = 'VIEW'

    //
    // state variables
    //

    // list of registered handlers
    var handlers

    // list of intents sent to this app
    //
    // it's never cleaned up, so that newly registered handlers (especially those registered a bit too late)
    // will still receive the list of intents.
    var intents

    // the cordova object (defaults to global one)
    var cordova

    // has init() been called or not already
    var initCalled

    // reset the state to default
    openwith.reset = function () {
        handlers = [];
        intents = [];
        cordova = root.cordova;
        initCalled = false;
    };

    // perform the initial reset
    openwith.reset();

    // change the cordova object (mostly for testing)
    openwith.setCordova = function (value) {
        cordova = value;
    };

    var findHandler = function (callback) {
        for (var i = 0; i < handlers.length; ++i) {
            if (handlers[i] === callback) {
                return i;
            }
        }
        return -1;
    };

    // registers a intent handler
    openwith.addHandler = function (callback) {
        if (typeof callback !== 'function') {
            throw new Error('invalid handler function');
        }
        if (findHandler(callback) >= 0) {
            throw new Error('handler already defined');
        }
        handlers.push(callback);
        intents.forEach(function handleIntent (intent) {
            callback(intent);
        });
    };

    openwith.numHandlers = function () {
        return handlers.length;
    };

    openwith.load = function (dataDescriptor, successCallback, errorCallback) {
        var loadSuccess = function (base64) {
            dataDescriptor.base64 = base64;

            if (successCallback) {
                successCallback(base64, dataDescriptor);
            }
        };

        var loadError = function (err) {
            if (errorCallback) {
                errorCallback(err, dataDescriptor);
            }
        };

        if (dataDescriptor.base64) {
            loadSuccess(dataDescriptor.base64);
        }
        else {
            cordova.exec(loadSuccess, loadError, PLUGIN_NAME, 'load', [dataDescriptor]);
        }
    };

    openwith.exit = function () {
        cordova.exec(null, null, PLUGIN_NAME, 'exit', []);
    };

    var onNewIntent = function (intent) {
        // process the new intent
        handlers.forEach(function (handler) {
            handler(intent);
        });
        intents.push(intent);
    };

    // Initialize the native side at startup
    openwith.init = function (successCallback, errorCallback) {
        if (initCalled) {
            throw new Error('init should only be called once');
        }
        initCalled = true;

        // callbacks have to be functions
        if (successCallback && typeof successCallback !== 'function') {
            throw new Error('invalid success callback');
        }
        if (errorCallback && typeof errorCallback !== 'function') {
            throw new Error('invalid error callback');
        }

        var initSuccess = function () {
            if (successCallback) successCallback();
        }
        var initError = function () {
            if (errorCallback) errorCallback();
        }

        cordova.exec(onNewIntent, null, PLUGIN_NAME, 'setHandler', []);
        cordova.exec(initSuccess, initError, PLUGIN_NAME, 'init', []);
    };

    return openwith;
};

// Export the plugin object
var openwith = initOpenwithPlugin(this);
module.exports = openwith;
this.plugins = this.plugins || {};
this.plugins.openwith = openwith;
