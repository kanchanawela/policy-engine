package com.policyengine.time;

import java.time.ZonedDateTime;

public interface TimeProvider {
    ZonedDateTime now();
}
