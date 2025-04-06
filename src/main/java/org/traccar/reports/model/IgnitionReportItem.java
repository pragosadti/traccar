/*
 * Copyright 2019 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.reports.model;


public class IgnitionReportItem extends BaseReportItem {
    private String geofence;
    private long duration;
    private String startTimeString;
    private String endTimeString;
    private Double engineHours;


    public String getStartTimeString() {
        return startTimeString;
    }

    public void setStartTimeString(String startTimeString) {
        this.startTimeString = startTimeString;
    }

    public String getEndTimeString() {
        return endTimeString;
    }

    public void setEndTimeString(String endTimeString) {
        this.endTimeString = endTimeString;
    }

    public Double getEngineHours() {
        return engineHours;
    }

    public void setEngineHours(Double engineHours) {
        this.engineHours = engineHours;
    }

    public String getGeofence() {
        return geofence;
    }

    public void setGeofence(String geofence) {
        this.geofence = geofence;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
}
