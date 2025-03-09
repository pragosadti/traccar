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
package org.traccar.reports;

import jakarta.inject.Inject;
import org.jxls.common.Context;
import org.jxls.util.JxlsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.reports.common.ReportUtils;
import org.traccar.reports.model.IgnitionReportItem;
import org.traccar.storage.StorageException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;

public class IgnitionReportProvider {

    private static final Logger log = LoggerFactory.getLogger(IgnitionReportProvider.class);

    private final ReportUtils reportUtils;
    private final Ignition ignition;

    @Inject
    public IgnitionReportProvider(ReportUtils reportUtils, Ignition ignition) {
        this.reportUtils = reportUtils;
        this.ignition = ignition;
    }

    public Collection<IgnitionReportItem> getIgnitionReportItems(Collection<Long> deviceIds, Long userId, Collection<Long> groupIds,
                                                                 Date from, Date to) throws Exception {
        return new ArrayList<>(ignition.getObjects(userId, deviceIds, groupIds, from, to));
    }


    public void getExcel(OutputStream outputStream, long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) {
        reportUtils.checkPeriodLimit(from, to);
        Collection<IgnitionReportItem> items;
        try {
            items = ignition.getObjects(userId, deviceIds, groupIds, from, to);
            items.forEach(IgnitionReportProvider::mapDateToString);
        } catch (SQLException | StorageException e) {
            throw new RuntimeException(e);
        }

        String templatePath = "templates/export";

        try (InputStream inputStream = Files.newInputStream(Paths.get(templatePath + "/ignition.xlsx"))) {
            Context jxlsContext = new Context();
            jxlsContext.putVar("items", items);
            jxlsContext.putVar("from", formatDate(from));
            jxlsContext.putVar("to", formatDate(to));
            JxlsHelper.getInstance().setUseFastFormulaProcessor(false)
                    .processTemplate(inputStream, outputStream, jxlsContext);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void mapDateToString(IgnitionReportItem ignitionReportItem) {
        ignitionReportItem.setStartTimeString(formatDate(ignitionReportItem.getStartTime()));
        ignitionReportItem.setEndTimeString(formatDate(ignitionReportItem.getEndTime()));
    }

    private static String formatDate(Date date) {
        SimpleDateFormat inputFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
        inputFormat.setTimeZone(TimeZone.getTimeZone("WET"));
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // Set the output time zone to the desired time zone (e.g., Central European Time (CET))
        outputFormat.setTimeZone(TimeZone.getTimeZone("CET"));
        String formattedDate = "";

        try {
            Date parsed = inputFormat.parse(date.toString());
            formattedDate = outputFormat.format(parsed);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return formattedDate;
    }
}
