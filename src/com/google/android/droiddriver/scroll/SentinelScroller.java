/*
 * Copyright (C) 2013 DroidDriver committers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.droiddriver.scroll;

import android.util.Log;

import com.google.android.droiddriver.DroidDriver;
import com.google.android.droiddriver.Poller;
import com.google.android.droiddriver.UiElement;
import com.google.android.droiddriver.exceptions.ElementNotFoundException;
import com.google.android.droiddriver.exceptions.TimeoutException;
import com.google.android.droiddriver.finders.By;
import com.google.android.droiddriver.finders.Finder;
import com.google.android.droiddriver.scroll.Direction.Axis;
import com.google.android.droiddriver.scroll.Direction.PhysicalDirection;
import com.google.android.droiddriver.util.Logs;

/**
 * A {@link Scroller} that looks for the desired item in the currently shown
 * content of the scrollable container, otherwise scrolls the container one step
 * at a time and look again, until we cannot scroll any more. A
 * {@link SentinelStrategy} is used to determine whether more scrolling is
 * possible.
 * <p>
 * This algorithm is needed unless the DroidDriver implementation supports
 * directly jumping to the item.
 */
public class SentinelScroller implements Scroller {
  private final int maxScrolls;
  private final long perScrollTimeoutMillis;
  private final Axis axis;
  private final SentinelStrategy sentinelStrategy;

  /**
   * @param maxScrolls the maximum number of scrolls. It should be large enough
   *        to allow any reasonable list size
   * @param perScrollTimeoutMillis the timeout in millis that we poll for the
   *        item after each scroll
   * @param axis the axis this scroller can scroll
   */
  public SentinelScroller(int maxScrolls, long perScrollTimeoutMillis, Axis axis,
      SentinelStrategy sentinelStrategy) {
    this.maxScrolls = maxScrolls;
    this.perScrollTimeoutMillis = perScrollTimeoutMillis;
    this.axis = axis;
    this.sentinelStrategy = sentinelStrategy;
  }

  /**
   * Constructs with default 100 maxScrolls, 1 second for
   * perScrollTimeoutMillis, vertical axis.
   */
  public SentinelScroller(SentinelStrategy sentinelStrategy) {
    this(100, 1000, Axis.VERTICAL, sentinelStrategy);
  }

  @Override
  public UiElement scrollTo(DroidDriver driver, Finder containerFinder, Finder itemFinder,
      PhysicalDirection direction) {
    Logs.call(this, "scrollTo", driver, containerFinder, itemFinder, direction);
    // Enforce itemFinder is relative to containerFinder.
    // Combine with containerFinder to make itemFinder absolute.
    itemFinder = By.chain(containerFinder, itemFinder);

    int i = 0;
    for (; i <= maxScrolls; i++) {
      try {
        return driver.getPoller()
            .pollFor(driver, itemFinder, Poller.EXISTS, perScrollTimeoutMillis);
      } catch (TimeoutException e) {
        if (i < maxScrolls && !sentinelStrategy.scroll(driver, containerFinder, direction)) {
          break;
        }
      }
    }

    ElementNotFoundException exception = new ElementNotFoundException(itemFinder);
    if (i == maxScrolls) {
      // This is often a program error -- maxScrolls is a safety net; we should
      // have either found itemFinder, or stopped to scroll b/c of reaching the
      // end. If maxScrolls is reasonably large, sentinelStrategy must be wrong.
      Logs.logfmt(Log.WARN, exception, "Scrolled %s %d times; sentinelStrategy=%s",
          containerFinder, maxScrolls, sentinelStrategy);
    }
    throw exception;
  }

  @Override
  public UiElement scrollTo(DroidDriver driver, Finder containerFinder, Finder itemFinder) {
    // TODO: start searching from beginning instead of the current location.
    for (PhysicalDirection direction : axis.getPhysicalDirections()) {
      try {
        return scrollTo(driver, containerFinder, itemFinder, direction);
      } catch (ElementNotFoundException e) {
        // try another direction
      }
    }
    throw new ElementNotFoundException(By.chain(containerFinder, itemFinder));
  }
}
