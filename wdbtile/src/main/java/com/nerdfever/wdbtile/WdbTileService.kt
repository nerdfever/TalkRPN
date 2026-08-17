package com.nerdfever.wdbtile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/*
 * The tile: one full-screen button. Tap anywhere, wireless debugging flips,
 * and the tile redraws showing the new state.
 *
 * The mechanism is the tiles API's LoadAction: tapping does not run app code
 * directly, it asks the system to request the tile again with the tapped
 * element's id attached. So the toggle happens inside onTileRequest, which is
 * also what redraws - one code path for both.
 */

// ---- Tweakables ------------------------------------------------------------

/** The lit-LED red, matched by eye to the calculator's display red. */
private const val COLOUR_ON = 0xFFFF0000.toInt()

/** The unlit state: the grey of the calculator's labels. */
private const val COLOUR_OFF = 0xFF8A8A8A.toInt()

/** The wedged state: the dot font's neon orange - alarming, and not red. */
private const val COLOUR_WEDGED = 0xFFFF5F1F.toInt()

/**
 * How often the system is asked to refresh the tile unprompted, so a wedged
 * adbd stops showing as ON within a refresh. Advisory - the system may batch.
 */
private const val FRESHNESS_MS = 5_000L

/** Text sizes, in sp. */
private const val TITLE_SP = 24f
private const val STATE_SP = 40f
private const val HINT_SP = 14f

// ---- Identity strings --------------------------------------------------------

/** The clickable id that marks a tile request as "the button was tapped". */
private const val TOGGLE_ID = "toggle"

/** Tiles cache pictures against this; we use none, so it never changes. */
private const val RESOURCES_VERSION = "1"

class WdbTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {

        // A tap is a request whose state carries the button's id; anything else
        // is just the system wanting a redraw (entering the carousel, say).
        val tapped = requestParams.currentState.lastClickableId == TOGGLE_ID

        // Flip on tap. The only way this fails is the missing one-time grant.
        var denied = false
        if (tapped) denied = !wdbSet(this, !wdbIsOn(this))

        // Draw whatever is now true - and re-check on a timer even untapped,
        // so a wedged adbd stops masquerading as ON at the next refresh.
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(layout(wdbState(this), wdbLivePort(), denied))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        )

    /** The whole face: a tappable box holding a title and the truth. */
    private fun layout(
        state: WdbState,
        livePort: Int,
        denied: Boolean,
    ): LayoutElementBuilders.LayoutElement {

        // Tapping the box reloads the tile with TOGGLE_ID attached - see above.
        val tapToToggle = ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId(TOGGLE_ID)
                    .setOnClick(ActionBuilders.LoadAction.Builder().build())
                    .build()
            )
            .build()

        // What to say, and in which colour. ON is only claimed when adbd is
        // really listening; a wedged service says so instead.
        val stateText = when {
            denied -> "NO PERMIT"
            else -> state.name
        }
        val stateColour = when {
            denied -> COLOUR_OFF
            state == WdbState.ON -> COLOUR_ON
            state == WdbState.WEDGED -> COLOUR_WEDGED
            else -> COLOUR_OFF
        }

        val column = LayoutElementBuilders.Column.Builder()
            .addContent(text("WDB", TITLE_SP, COLOUR_OFF))
            .addContent(text(stateText, if (denied) HINT_SP else STATE_SP, stateColour))
            .apply {
                // The grant is a PC-side act, so say so on the watch.
                if (denied) addContent(text("grant via adb", HINT_SP, COLOUR_OFF))

                // Proof of life: the port adbd is listening on.
                if (!denied && state == WdbState.ON) {
                    addContent(text(":$livePort", HINT_SP, COLOUR_ON))
                }

                // The revival ritual: off, then on again.
                if (!denied && state == WdbState.WEDGED) {
                    addContent(text("tap twice to revive", HINT_SP, COLOUR_OFF))
                }
            }
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(tapToToggle)
            .addContent(column)
            .build()
    }

    /** One line of text. */
    private fun text(s: String, sizeSp: Float, colour: Int): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(sizeSp))
                    .setColor(argb(colour))
                    .build()
            )
            .build()
}
