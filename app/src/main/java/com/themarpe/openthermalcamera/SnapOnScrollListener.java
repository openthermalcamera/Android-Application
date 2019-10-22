package com.themarpe.openthermalcamera;


import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

/*
    Adapted from Nick Rout's snippet
    https://medium.com/over-engineering/detecting-snap-changes-with-androids-recyclerview-snaphelper-9e9f5e95c424
 */

class SnapOnScrollListener extends RecyclerView.OnScrollListener{

    enum Behavior {
        NOTIFY_ON_SCROLL,
        NOTIFY_ON_SCROLL_STATE_IDLE;
    }

    interface OnSnapPositionChangeListener{
        void onSnapPositionChange(int position);
    }

    SnapOnScrollListener(SnapHelper sh, Behavior b, OnSnapPositionChangeListener cl){
        snapHelper = sh;
        behavior = b;
        listener = cl;
    }

    int snapPosition = RecyclerView.NO_POSITION;
    private SnapHelper snapHelper;
    private Behavior behavior;
    private OnSnapPositionChangeListener listener = null;


    int getSnapPosition(SnapHelper sh, RecyclerView recyclerView) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if(layoutManager == null) return RecyclerView.NO_POSITION;
        View snapView = snapHelper.findSnapView(layoutManager);
        if(snapView == null) return RecyclerView.NO_POSITION;
        return layoutManager.getPosition(snapView);
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
        if (behavior == Behavior.NOTIFY_ON_SCROLL) {
            maybeNotifySnapPositionChange(recyclerView);
        }
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        super.onScrollStateChanged(recyclerView, newState);
        if (behavior == Behavior.NOTIFY_ON_SCROLL_STATE_IDLE
                && newState == RecyclerView.SCROLL_STATE_IDLE) {
            maybeNotifySnapPositionChange(recyclerView);
        }
    }

    private void maybeNotifySnapPositionChange(RecyclerView recyclerView) {
        int curPosition = getSnapPosition(snapHelper, recyclerView);
        if (curPosition != snapPosition) {
            if(listener != null){
                listener.onSnapPositionChange(curPosition);
            }
            snapPosition = curPosition;
        }
    }

}