package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HomeGestureControllerTest {
    @Test public void firstHomeReturnsTvHome() {
        HomeGestureController controller = new HomeGestureController();
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(0));
    }

    @Test public void twoHomesDoNotEnterStockHome() {
        HomeGestureController controller = new HomeGestureController();
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(0));
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(300));
    }

    @Test public void threeAdjacentHomesEnterStockOnce() {
        HomeGestureController controller = new HomeGestureController();
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(0));
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(300));
        assertEquals(HomeGestureController.Result.STOCK_HOME, controller.onHome(600));
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(900));
    }

    @Test public void boundaryThreeHomesAreAccepted() {
        HomeGestureController controller = new HomeGestureController();
        controller.onHome(0);
        controller.onHome(600);
        assertEquals(HomeGestureController.Result.STOCK_HOME, controller.onHome(1200));
    }

    @Test public void gapsOverSixHundredDoNotTriple() {
        HomeGestureController controller = new HomeGestureController();
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(0));
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(700));
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(1400));
    }

    @Test public void longRepeatAndCanceledEventsDoNotCount() {
        HomeGestureController controller = new HomeGestureController();
        assertEquals(HomeGestureController.Result.NONE, controller.onHome(0, true, false, false));
        assertEquals(HomeGestureController.Result.NONE, controller.onHome(1, false, true, false));
        assertEquals(HomeGestureController.Result.NONE, controller.onHome(2, false, false, true));
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(3));
    }

    @Test public void duplicateDeliveryAtSameTimestampCountsOnce() {
        HomeGestureController controller = new HomeGestureController();
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(100));
        assertEquals(HomeGestureController.Result.NONE, controller.onHome(100));
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(300));
        assertEquals(HomeGestureController.Result.STOCK_HOME, controller.onHome(500));
    }

    @Test public void lifecycleResetPreventsSleepWakeTriple() {
        HomeGestureController controller = new HomeGestureController();
        controller.onHome(0);
        controller.onHome(300);
        controller.reset();
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(1000));
    }

    @Test public void resetAfterStockLeavesNextHomeOnTvHome() {
        HomeGestureController controller = new HomeGestureController();
        controller.onHome(0);
        controller.onHome(300);
        assertEquals(HomeGestureController.Result.STOCK_HOME, controller.onHome(600));
        assertEquals(HomeGestureController.Result.NEW_HOME, controller.onHome(30_600));
    }
}
