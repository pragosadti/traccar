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
import org.traccar.reports.common.ReportUtils;
import org.traccar.reports.model.IgnitionReportItem;
import org.traccar.storage.StorageException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import static org.traccar.helper.DateUtil.formatDate;

public class IgnitionReportProvider {


    private final ReportUtils reportUtils;
    private final Ignition ignition;

    @Inject
    public IgnitionReportProvider(ReportUtils reportUtils, Ignition ignition) {
        this.reportUtils = reportUtils;
        this.ignition = ignition;
    }

    public Collection<IgnitionReportItem> getIgnitionReportItems(Collection<Long> deviceIds, Long userId, Collection<Long> groupIds,
                                                                 Date from, Date to, Boolean grouped) throws Exception {
        return new ArrayList<>(ignition.getObjects(userId, deviceIds, groupIds, from, to, grouped));
    }


    public void getExcel(OutputStream outputStream, long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
                         Date from, Date to, Boolean grouped) {
        reportUtils.checkPeriodLimit(from, to);
        Collection<IgnitionReportItem> items;
        try {
            items = ignition.getObjects(userId, deviceIds, groupIds, from, to, grouped);
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
}
