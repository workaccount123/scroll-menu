package com.aliyun.menu;

/**
 * @author appmac
 */
public interface OnMenuListener {

    void onScroll(int dx, int dy);

    void onStatusChange(int oldStatus, int curStatus);
}
