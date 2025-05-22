package org.traccar.reports;

import jakarta.inject.Inject;
import org.traccar.config.Config;
import org.traccar.helper.DistanceCalculator;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.helper.model.GeofenceUtil;
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.Device;
import org.traccar.model.Geofence;
import org.traccar.model.Position;
import org.traccar.reports.model.BaseReportItem;
import org.traccar.reports.model.IgnitionReportItem;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;
import static org.traccar.helper.DateUtil.formatDate;

public class Ignition {

    private final Storage storage;
    private final Config config;
    private static final double MILLISECONDS_IN_HOUR = 3600.0 * 1000.0;

    @Inject
    public Ignition(Storage storage, Config config) {
        this.storage = storage;
        this.config = config;
    }

    private static IgnitionReportItem toGroupedItem(List<IgnitionReportItem> list) {
        if (list.isEmpty()) {
            return null;
        }

        IgnitionReportItem first = list.stream().findFirst().orElseThrow();
        IgnitionReportItem groupedItem = new IgnitionReportItem();
        groupedItem.setDeviceName(first.getDeviceName());
        groupedItem.setDeviceId(first.getDeviceId());
        groupedItem.setGeofence(first.getGeofence());

        String date = first.getStartTimeString().substring(0, 10);
        groupedItem.setStartTimeString(date + " 00:00:00");
        groupedItem.setEndTimeString(date + " 23:59:59");
        groupedItem.setStartTime(first.getStartTime());

        long totalDuration = list.stream().mapToLong(IgnitionReportItem::getDuration).sum();
        double totalDistance = list.stream().mapToDouble(IgnitionReportItem::getDistance).sum();
        double totalEngineHours = list.stream().mapToDouble(IgnitionReportItem::getEngineHours).sum();

        groupedItem.setDuration(totalDuration);
        groupedItem.setDistance(totalDistance);
        groupedItem.setEngineHours(totalEngineHours);

        return groupedItem;
    }

    private static String format(IgnitionReportItem item) {
        return "%d-%s-%s".formatted(item.getDeviceId(), item.getGeofence().trim().toLowerCase(), item.getStartTimeString().substring(0, 10));
    }

    private IgnitionReportItem calculateIgnitionReport(
            Device device, long geofenceId, Position start, Position end) throws StorageException {
        IgnitionReportItem report = new IgnitionReportItem();
        report.setDeviceId(device.getId());
        report.setDeviceName(device.getName());
        report.setGeofence(GeofenceUtil.getAllGeofences(storage).stream().filter(geofence -> geofence.getId() == geofenceId).toList().get(0).getName());
        report.setStartTime(start.getFixTime());
        report.setEndTime(end.getFixTime());
        report.setDuration(end.getFixTime().getTime() - start.getFixTime().getTime());
        report.setEngineHours(report.getDuration() / MILLISECONDS_IN_HOUR);

        double distance = DistanceCalculator.distance(
                start.getLatitude(), start.getLongitude(),
                end.getLatitude(), end.getLongitude());

        report.setDistance(distance);
        return report;
    }

    private List<IgnitionReportItem> calculateDeviceReport(List<Geofence> geofenceList, Device device, Date from, Date to) throws StorageException {
        List<IgnitionReportItem> result = new ArrayList<>();
        List<Position> positions = PositionUtil.getPositions(storage, device.getId(), from, to);

        if (!positions.isEmpty()) {
            int startIndex = -1;
            long startGeofence = -1;

            for (int i = 0; i < positions.size(); i++) {
                Position position = positions.get(i);
                boolean ignition = position.getBoolean(Position.KEY_IGNITION);
                long geofence = getGeofenceForPosition(geofenceList, position);

                if (startIndex >= 0) {
                    if (!ignition || geofence != startGeofence) {
                        result.add(calculateIgnitionReport(device, startGeofence, positions.get(startIndex), position));
                        startIndex = -1;
                        startGeofence = -1;
                    }
                }

                if (startIndex < 0 && ignition && geofence > 0) {
                    startIndex = i;
                    startGeofence = geofence;
                }
            }

            if (startIndex >= 0 && startIndex < positions.size()) {
                result.add(calculateIgnitionReport(device, startGeofence, positions.get(startIndex), positions.get(positions.size() - 1)));
            }
        }
        return result;
    }

    private long getGeofenceForPosition(List<Geofence> geofenceList, Position position) {
        for (Geofence geofence : geofenceList) {
            if (geofence.getGeometry().containsPoint(config, geofence, position.getLatitude(), position.getLongitude())) {
                return geofence.getId();
            }
        }

        return -1;
    }

    public Collection<IgnitionReportItem> getObjects(long userId, Collection<Long> deviceIds,
                                                     Collection<Long> groupIds, Date from, Date to, Boolean grouped) throws SQLException, StorageException {
        List<IgnitionReportItem> result = new ArrayList<>();
        List<Geofence> geofenceList = GeofenceUtil.getAllGeofences(storage);

        for (Device device : DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds)) {
            result.addAll(calculateDeviceReport(geofenceList, device, from, to));
        }
        result.forEach(this::mapDateToString);

        if (grouped) {
            Map<String, IgnitionReportItem> groupedItemsMap = result.stream()
                    .collect(Collectors.groupingBy(Ignition::format,
                            collectingAndThen(Collectors.toList(), Ignition::toGroupedItem)));
            List<IgnitionReportItem> sortedGroupedItems = new ArrayList<>(groupedItemsMap.values());
            sortedGroupedItems.sort(Comparator.comparing(BaseReportItem::getStartTime));

            return sortedGroupedItems;
        }

        return result.stream().sorted(Comparator.comparing(BaseReportItem::getStartTime)).collect(Collectors.toList());
    }

    private void mapDateToString(IgnitionReportItem ignitionReportItem) {
        ignitionReportItem.setStartTimeString(formatDate(ignitionReportItem.getStartTime()));
        ignitionReportItem.setEndTimeString(formatDate(ignitionReportItem.getEndTime()));
    }
}
