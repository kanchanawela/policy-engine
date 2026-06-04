package com.policyengine.time;

import java.time.ZonedDateTime;

public class SystemTimeProvider implements TimeProvider {

    @Override
    public ZonedDateTime now() {
        return ZonedDateTime.now();
    }
}
