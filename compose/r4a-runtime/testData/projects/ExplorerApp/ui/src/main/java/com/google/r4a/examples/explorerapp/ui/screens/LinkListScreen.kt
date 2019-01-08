package com.google.r4a.examples.explorerapp.ui.screens

import android.support.design.widget.AppBarLayout
import android.support.design.widget.CoordinatorLayout
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import com.google.r4a.Composable
import com.google.r4a.adapters.setLayoutHeight
import com.google.r4a.adapters.setLayoutWidth
import com.google.r4a.composer
import com.google.r4a.examples.explorerapp.common.adapters.Tabs
import com.google.r4a.examples.explorerapp.ui.Colors


class LinkListScreen {
    private val tlParams = AppBarLayout.LayoutParams(
            AppBarLayout.LayoutParams.MATCH_PARENT,
            AppBarLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
    }

    private val pagerParams = CoordinatorLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
    ).apply {
        behavior = AppBarLayout.ScrollingViewBehavior()
    }

    // TODO(lmr): change this to be the user's saved list of subreddits when a user is logged in
    private val subreddits = listOf("science", "androiddev", "javascript", "reactjs")

    @Composable
    operator fun invoke() {
        val subreddits = subreddits // TODO(lmr): remove when private access works
        <CoordinatorLayout
            layoutWidth=MATCH_PARENT
            layoutHeight=MATCH_PARENT
        >
            <Tabs
                tabBackgroundColor=Colors.WHITE
                tabLayoutParams=tlParams
                pagerLayoutParams=pagerParams
                offscreenPageLimit=2
                titles=subreddits
            > tabs, content ->
                <RedditAppBar>
                    <tabs />
                </RedditAppBar>
                // once we have generics, it's possible we could have the object get passed directly into
                // the children function
                <content
                    children={ tabIndex ->
                        val subreddit = subreddits[tabIndex]
                        <SubredditLinkList subreddit />
                    }
                />
            </Tabs>
        </CoordinatorLayout>
    }
}

