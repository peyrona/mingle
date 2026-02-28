
"use strict";

/**
 * GumScheduler — A gadget for visually configuring and monitoring Kronos cron schedules.
 *
 * Integrates with the Kronos controller (which uses the Cron class) to allow users
 * to configure schedules (Daily, Weekly, Monthly, Yearly) from the Gum web interface.
 * The schedule UI is rendered inline in the gadget; the properties dialog handles
 * only ExEn/device selection and styling.
 */
class GumScheduler extends GumGadget
{
    constructor( props )
    {
        super( "scheduler", props );

        if( p_base.isEmpty( props ) )
        {
            this.width  = gum.isUsingFreeLayout() ? 380 : "100%";
            this.height = gum.isUsingFreeLayout() ? 320 : "100%";

            this.exen     = null;
            this.device   = null;
            this.mode     = "daily";
            this.time     = ["09:00"];
            this.dow      = [];          // Day-of-week numbers (0=Sun, 1=Mon..6=Sat) for weekly
            this.dom      = [1];         // Day-of-month (1-31) for monthly/yearly
            this.month    = [1];         // Month numbers (1-12) for yearly
            this.every    = 1;           // Interval multiplier
            this.start    = null;        // Optional start date-time
            this.stop     = null;        // Optional stop date-time
            this.is24Hour = true;
            this.lastRun  = null;        // Last execution timestamp (runtime only, from device)
        }
    }

    //-----------------------------------------------------------------------------------------//

    /**
     * Opens the properties dialog for ExEn/device selection.
     */
    edit( bStart = true )
    {
        super.edit( bStart );

        if( bStart )
        {
            let self = this;

            dlgScheduler.setup( this,
                                () => self.show(),
                                () => self.edit( false ) )
                        .show();
        }

        return this;
    }

    show( isOngoing = false )
    {
        if( isOngoing )
            return this;

        this._buildUI_();
        this._updateListener_();

        return super.show( isOngoing );
    }

    /**
     * Returns a copy of this gadget's properties (without runtime-only fields).
     */
    distill()
    {
        return super.distill( ["lastRun"] );
    }

    //-----------------------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Listens to the Kronos device. On value change, updates the "Last run" display.
     */
    _updateListener_()
    {
        this._resetListeners();

        if( this.exen && this.device )
        {
            let self = this;

            let fn = function( action, payload )
                     {
                         if( payload.value !== undefined && payload.value !== null )
                         {
                             self.lastRun = payload.value;
                             self._updateLastRun_();
                         }

                         self._executeUserCode_( action, payload );
                     };

            this._addListener( this.exen, this.device, fn );
        }
    }

    /**
     * Updates the "Last run" label in the gadget footer.
     */
    _updateLastRun_()
    {
        let $area = this.getContentArea();
        let $info = $area.find('.sched-last-run');

        if( $info.length > 0 && this.lastRun )
            $info.text( 'Last run: ' + this.lastRun );
    }

    /**
     * Builds the scheduler UI by cloning the template and populating dynamic content.
     */
    _buildUI_()
    {
        const self = this;
        let $area  = this.getContentArea().empty();

        // Clone the template
        let $tpl = GumGadget.cloneTemplate( "tpl-scheduler" );

        $area.append( $tpl );

        // --- 24h/12h toggle state ---
        if( this.is24Hour )  $area.find('.sched-btn-24h').addClass('is-info is-selected');
        else                 $area.find('.sched-btn-12h').addClass('is-info is-selected');

        // --- Set active tab ---
        $area.find('.tabs li').removeClass('is-active');
        $area.find('.tabs li[data-mode="' + this.mode + '"]').addClass('is-active');

        // --- Populate time pickers into containers ---
        $area.find('.sched-time-daily-container'  ).html( this._buildTimePicker_( 'sched-time-daily',   this.time[0] || '09:00' ) );
        $area.find('.sched-time-weekly-container' ).html( this._buildTimePicker_( 'sched-time-weekly',  this.time[0] || '09:00' ) );
        $area.find('.sched-time-monthly-container').html( this._buildTimePicker_( 'sched-time-monthly', this.time[0] || '09:00' ) );
        $area.find('.sched-time-yearly-container' ).html( this._buildTimePicker_( 'sched-time-yearly',  this.time[0] || '09:00' ) );

        // --- Restore state to inputs ---
        $area.find('.sched-every-daily').val( this.every );
        $area.find('.sched-every-weekly').val( this.every );
        $area.find('.sched-every-monthly').val( this.every );
        $area.find('.sched-dom-monthly').val( this.dom[0] || 1 );
        $area.find('.sched-dom-yearly').val( this.dom[0] || 1 );
        $area.find('.sched-month-yearly').val( this.month[0] || 1 );

        // --- Day-of-week button state (weekly) ---
        $area.find('.sched-dow-btn').each( function()
        {
            if( self.dow.includes( parseInt( $(this).data('val') ) ) )
                $(this).addClass('is-info is-selected');
        });

        // --- Start/Stop fields ---
        if( this.start ) $area.find('.sched-start').val( this.start );
        if( this.stop )  $area.find('.sched-stop').val( this.stop );

        // --- Show active panel, hide others ---
        let $panels = $area.find('.sched-panels');

        $panels.children('.sched-panel').hide();
        $panels.find('.sched-panel-' + this.mode).show();

        // --- Update last run ---
        this._updateLastRun_();

        // ========================
        // Event handlers
        // ========================

        // Tab switching
        let $tabs = $area.find('.tabs');

        $tabs.find('li').on('click', function()
        {
            $tabs.find('li').removeClass('is-active');
            $(this).addClass('is-active');
            self.mode = $(this).data('mode');
            $panels.children('.sched-panel').hide();
            $panels.find('.sched-panel-' + self.mode).show();
        });

        // 24h / 12h toggle
        $area.find('.sched-btn-24h, .sched-btn-12h').on('click', function()
        {
            $area.find('.sched-btn-24h, .sched-btn-12h').removeClass('is-selected is-info');
            $(this).addClass('is-selected is-info');
            self.is24Hour = $(this).hasClass('sched-btn-24h');
            self._buildUI_();   // Re-render to switch time format
        });

        // Day-of-week buttons (weekly)
        $area.find('.sched-dow-btn').on('click', function()
        {
            $(this).toggleClass('is-info is-selected');
            self._readUIState_( $area );
        });

        // All input/select changes
        $area.find('input, select').not('.sched-dow-btn').on('change', function()
        {
            self._readUIState_( $area );
        });

        // Apply button
        $area.find('.sched-apply').on('click', function()
        {
            self._readUIState_( $area );
            self._sendConfig_();
        });
    }

    /**
     * Builds a time picker (hour:minute selects) as an HTML string.
     * This remains as a JS-generated string because the option lists are
     * fully dynamic (24h vs 12h mode, selected values).
     *
     * @param {String} cssClass  CSS class prefix to identify this picker
     * @param {String} timeStr   Time in "HH:MM" format
     * @returns {String} HTML string for the time picker controls
     */
    _buildTimePicker_( cssClass, timeStr )
    {
        const pad = (n) => n < 10 ? '0' + n : n;

        let h = 9, m = 0;

        if( timeStr )
        {
            let parts = timeStr.split(':');
            h = parseInt( parts[0] ) || 0;
            m = parseInt( parts[1] ) || 0;
        }

        let html = '<div class="field is-grouped is-align-items-center">';

        // Hour select
        html += '<div class="control"><div class="select is-small"><select class="' + cssClass + '-h">';

        if( this.is24Hour )
        {
            for( let i = 0; i < 24; i++ )
            {
                let sel = (i === h) ? ' selected' : '';
                html += '<option value="' + i + '"' + sel + '>' + pad(i) + '</option>';
            }
        }
        else
        {
            let h12 = h % 12;
            if( h12 === 0 ) h12 = 12;

            for( let i = 1; i <= 12; i++ )
            {
                let sel = (i === h12) ? ' selected' : '';
                html += '<option value="' + i + '"' + sel + '>' + pad(i) + '</option>';
            }
        }

        html += '</select></div></div>';
        html += '<div class="control"><span class="has-text-weight-bold">:</span></div>';

        // Minute select
        html += '<div class="control"><div class="select is-small"><select class="' + cssClass + '-m">';

        for( let i = 0; i < 60; i++ )
        {
            let sel = (i === m) ? ' selected' : '';
            html += '<option value="' + i + '"' + sel + '>' + pad(i) + '</option>';
        }

        html += '</select></div></div>';

        // AM/PM for 12h mode
        if( ! this.is24Hour )
        {
            let isPM = h >= 12;

            html += '<div class="control"><div class="select is-small"><select class="' + cssClass + '-mer">' +
                        '<option value="AM"' + (!isPM ? ' selected' : '') + '>AM</option>' +
                        '<option value="PM"' + (isPM  ? ' selected' : '') + '>PM</option>' +
                    '</select></div></div>';
        }

        html += '</div>';

        return html;
    }

    /**
     * Reads a time value from a time picker in the DOM.
     *
     * @param {jQuery} $area   The content area
     * @param {String} prefix  CSS class prefix (e.g. 'sched-time-daily')
     * @returns {String} Time in "HH:MM" 24h format
     */
    _readTimePicker_( $area, prefix )
    {
        let h = parseInt( $area.find('.' + prefix + '-h').val() ) || 0;
        let m = parseInt( $area.find('.' + prefix + '-m').val() ) || 0;

        if( ! this.is24Hour )
        {
            let mer = $area.find('.' + prefix + '-mer').val();

            if( mer === 'PM' && h < 12 ) h += 12;
            if( mer === 'AM' && h === 12 ) h = 0;
        }

        const pad = (n) => n < 10 ? '0' + n : n;

        return pad(h) + ':' + pad(m);
    }

    /**
     * Reads current UI selections into gadget properties.
     */
    _readUIState_( $area )
    {
        // Time from active panel
        this.time = [ this._readTimePicker_( $area, 'sched-time-' + this.mode ) ];

        // Mode-specific fields
        switch( this.mode )
        {
            case 'daily':
                this.every = parseInt( $area.find('.sched-every-daily').val() ) || 1;
                break;

            case 'weekly':
                this.dow = [];
                let self = this;
                $area.find('.sched-dow-btn.is-selected').each( function() { self.dow.push( parseInt( $(this).data('val') ) ); } );
                this.dow.sort();
                this.every = parseInt( $area.find('.sched-every-weekly').val() ) || 1;
                break;

            case 'monthly':
                this.dom   = [ parseInt( $area.find('.sched-dom-monthly').val() ) || 1 ];
                this.every = parseInt( $area.find('.sched-every-monthly').val() ) || 1;
                break;

            case 'yearly':
                this.dom   = [ parseInt( $area.find('.sched-dom-yearly').val() ) || 1 ];
                this.month = [ parseInt( $area.find('.sched-month-yearly').val() ) || 1 ];
                break;
        }

        // Start/Stop
        let startVal = $area.find('.sched-start').val();
        let stopVal  = $area.find('.sched-stop').val();

        this.start = (startVal && startVal.length > 0) ? startVal : null;
        this.stop  = (stopVal  && stopVal.length > 0)  ? stopVal  : null;
    }

    /**
     * Sends the current schedule configuration to the Kronos device via WebSocket.
     */
    _sendConfig_()
    {
        if( ! this.exen || ! this.device )
            return;

        let config = this._buildCronConfig_();
        let sValue = JSON.stringify( config );

        gum_ws_boards.requestChange( this.exen, this.device, sValue );
    }

    /**
     * Converts the current UI state into a Cron-compatible config object.
     *
     * @returns {Object} Configuration object with keys: mode, time, dow, dom, month, every, start, stop
     */
    _buildCronConfig_()
    {
        return {
            mode  : this.mode,
            time  : this.time,
            dow   : this.dow,
            dom   : this.dom,
            month : this.month,
            every : this.every,
            start : this.start,
            stop  : this.stop
        };
    }
}
