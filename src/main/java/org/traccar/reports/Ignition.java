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
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;
import static org.traccar.helper.DateUtil.formatDate;

/**
 * Generates ignition reports, detailing periods when a device's ignition was on,
 * including duration, distance traveled, and engine hours within specified geofences.
 * The report can provide individual ignition events or events grouped by device, geofence, and day.
 */
public class Ignition {

    private final Storage storage;
    private final Config config;
    private static final double MILLISECONDS_IN_HOUR = 3600.0 * 1000.0;

    /**
     * Constructs an Ignition report generator.
     *
     * @param storage The storage instance for accessing data.
     * @param config  The system configuration.
     */
    @Inject
    public Ignition(Storage storage, Config config) {
        this.storage = storage;
        this.config = config;
    }

    /**
     * Aggregates a list of {@link IgnitionReportItem} into a single summary item.
     * The grouped item summarizes total duration, distance, and engine hours,
     * and uses the start time details of the first item in the list for its date representation.
     * Device details and geofence are taken from the first item.
     *
     * @param list The list of ignition report items to group.
     * @return A single {@link IgnitionReportItem} summarizing the list, or {@code null} if the input list is empty.
     * @throws java.util.NoSuchElementException if the list is not empty but {@code findFirst()} fails unexpectedly (should not happen for sequential streams).
     */
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

    /**
     * Generates a formatting string key for grouping {@link IgnitionReportItem} instances.
     * The key is based on device ID, geofence name (trimmed and lowercased), and the date part of the start time.
     *
     * @param item The ignition report item to format.
     * @return A string key for grouping.
     */
    private static String format(IgnitionReportItem item) {
        return "%d-%s-%s".formatted(
                item.getDeviceId(),
                item.getGeofence().trim().toLowerCase(),
                item.getStartTimeString().substring(0, 10));
    }

    /**
     * Calculates details for a single ignition-on period using a pre-fetched list of geofences.
     * This includes setting device information, geofence name, start/end times, duration,
     * engine hours, and distance traveled.
     *
     * @param geofenceList A pre-fetched list of {@link Geofence} objects to search within.
     * @param device     The device for which the report is generated.
     * @param geofenceId The ID of the geofence in which the ignition event occurred.
     * @param start      The starting {@link Position} of the ignition-on period.
     * @param end        The ending {@link Position} of the ignition-on period.
     * @return An {@link IgnitionReportItem} detailing the ignition event.
     * @throws StorageException If there's an issue accessing storage (though less likely in this refactored version for geofences).
     */
    private IgnitionReportItem calculateIgnitionReport(
            List<Geofence> geofenceList, Device device, long geofenceId, Position start, Position end) throws StorageException {
        IgnitionReportItem report = new IgnitionReportItem();
        report.setDeviceId(device.getId());
        report.setDeviceName(device.getName());
        report.setGeofence(geofenceList.stream()
                .filter(geofence -> geofence.getId() == geofenceId)
                .findFirst()
                .map(Geofence::getName)
                .orElse("Unknown Geofence " + geofenceId)); // Fallback if geofenceId not found in the list
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

    /**
     * Calculates all ignition-on report items for a specific device within a given time range.
     * It processes positions to identify periods where ignition was on and the device was within a geofence.
     *
     * @param geofenceList A pre-fetched list of all relevant {@link Geofence} objects to use for lookups.
     * @param device       The device to calculate the report for.
     * @param from         The start date/time of the reporting period.
     * @param to           The end date/time of the reporting period.
     * @return A list of {@link IgnitionReportItem} for the device.
     * @throws StorageException If there's an issue accessing position data.
     */
    private List<IgnitionReportItem> calculateDeviceReport(List<Geofence> geofenceList, Device device, Date from, Date to) throws StorageException {
        List<IgnitionReportItem> result = new ArrayList<>();
        List<Position> positions = PositionUtil.getPositions(storage, device.getId(), from, to);

        if (!positions.isEmpty()) {
            int startIndex = -1;
            long startGeofenceId = -1;

            for (int i = 0; i < positions.size(); i++) {
                Position position = positions.get(i);
                boolean ignition = position.getBoolean(Position.KEY_IGNITION);
                long currentGeofenceId = getGeofenceForPosition(geofenceList, position);

                if (startIndex >= 0) {
                    if (!ignition || currentGeofenceId != startGeofenceId) {
                        result.add(calculateIgnitionReport(geofenceList, device, startGeofenceId, positions.get(startIndex), position));
                        startIndex = -1;
                        startGeofenceId = -1;
                    }
                }

                if (startIndex < 0 && ignition && currentGeofenceId > 0) {
                    startIndex = i;
                    startGeofenceId = currentGeofenceId;
                }
            }

            if (startIndex >= 0 && startIndex < positions.size()) { // Check if still less than size for safety
                result.add(calculateIgnitionReport(geofenceList, device, startGeofenceId, positions.get(startIndex), positions.get(positions.size() - 1)));
            }
        }
        return result;
    }

    /**
     * Determines the ID of the geofence containing the given position.
     *
     * @param geofenceList A list of {@link Geofence} objects to check against.
     * @param position     The {@link Position} to check.
     * @return The ID of the first geofence found to contain the position, or -1 if not in any geofence.
     */
    private long getGeofenceForPosition(List<Geofence> geofenceList, Position position) {
        for (Geofence geofence : geofenceList) {
            if (geofence.getGeometry().containsPoint(config, geofence, position.getLatitude(), position.getLongitude())) {
                return geofence.getId();
            }
        }
        return -1;
    }

    /**
     * Retrieves a collection of ignition report items based on the provided criteria.
     * The items can be returned as a flat list or grouped by device, geofence, and day.
     * All results are sorted by start time.
     *
     * @param userId    The ID of the user requesting the report.
     * @param deviceIds A collection of device IDs to include in the report.
     * @param groupIds  A collection of group IDs (devices belonging to these groups will be included).
     * @param from      The start {@link Date} of the reporting period.
     * @param to        The end {@link Date} of the reporting period.
     * @param grouped   A boolean indicating whether to group the results.
     * If true, items are grouped by device, geofence, and day, with summarized values.
     * If false, individual ignition events are returned.
     * @return A collection of {@link IgnitionReportItem} objects, sorted by start time.
     * @throws SQLException     If a database access error occurs.
     * @throws StorageException If any other storage-related error occurs.
     */
    public Collection<IgnitionReportItem> getObjects(long userId, Collection<Long> deviceIds,
                                                     Collection<Long> groupIds, Date from, Date to, Boolean grouped)
            throws SQLException, StorageException {
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
            sortedGroupedItems.removeIf(Objects::isNull); // Safely remove any nulls
            sortedGroupedItems.sort(Comparator.comparing(BaseReportItem::getStartTime));
            return sortedGroupedItems;
        }

        return result.stream()
                .sorted(Comparator.comparing(BaseReportItem::getStartTime))
                .collect(Collectors.toList());
    }

    /**
     * Populates the string representations of start and end times for an {@link IgnitionReportItem}.
     *
     * @param ignitionReportItem The report item to update.
     */
    private void mapDateToString(IgnitionReportItem ignitionReportItem) {
        if (ignitionReportItem.getStartTime() != null) {
            ignitionReportItem.setStartTimeString(formatDate(ignitionReportItem.getStartTime()));
        }
        if (ignitionReportItem.getEndTime() != null) {
            ignitionReportItem.setEndTimeString(formatDate(ignitionReportItem.getEndTime()));
        }
    }
}
