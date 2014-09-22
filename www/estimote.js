var cordova = require('cordova'),
    exec = require('cordova/exec');

function handlers() {
    return estimote.channels.beaconfound.numHandlers;
}

/**
 * Create a new instance of Estimote(Plugin).
 * 
 * @class       Estimote
 * @classdesc   EstimotePlugin for cordova 3.0.0+ (PhoneGap).
 */
var Estimote = function() 
{
    this.platforms = [ "android" ];
    this._uuid = null;
    // Create new event handlers on the window (returns a channel instance)
    this.channels = {
        beaconfound:cordova.addWindowEventHandler("beaconfound")
    };
    for (var key in this.channels) {
        this.channels[key].onHasSubscribersChange = Estimote.onHasSubscribersChange;
    }
};

/**
 * Event handlers for when callbacks get registered for Estimote.
 * Keep track of how many handlers we have so we can start and stop the native ranging listener
 * appropriately (and hopefully save on battery life!).
 */
Estimote.onHasSubscribersChange = function() {
    // If we just registered the first handler, make sure native listener is started.
    if (this.numHandlers === 1 && handlers() === 1) {
        exec(estimote._status, estimote._error, "Estimote", "start", []);
    } else if (handlers() === 0) {
        exec(null, null, "Estimote", "stop", []);
    }
};

/**
 * Callback for beacon found
 *
 * @param {Object} beacon
 */
Estimote.prototype._status = function (beacon) {

    if (beacon) {
        if (estimote._uuid !== beacon.proximityUUID) {

            if(beacon.proximityUUID == null) {
                return; // special case where callback is called because we stopped listening to the native side.
            }

            // Something changed. Fire beaconfound event
            cordova.fireWindowEvent("beaconfound", beacon);

            estimote._uuid = beacon.proximityUUID;
        }
    }
};


/**
 * Error callback for estimote start
 */
Estimote.prototype._error = function(e) {
    console.log("Error initializing Estimote: " + e);
};

var estimote   = new Estimote();
module.exports  = estimote;
