package edu.seu.vcampus.common.student;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Assigns freshmen to size-balanced, gender-proportional classes by major. */
public final class FreshmanClassAssigner {
    private static final int MAX_CLASS_SIZE = 35;

    private FreshmanClassAssigner() { }

    /** Assigns rows independently by department and major for one enrollment year. */
    public static List<FreshmanClassAssignment> assign(List<FreshmanAdmissionRow> rows, int enrollmentYear) {
        if (enrollmentYear < 2000 || enrollmentYear > 2099) throw new IllegalArgumentException("invalid enrollmentYear");
        Objects.requireNonNull(rows, "rows");
        Map<String, List<FreshmanAdmissionRow>> groups = new LinkedHashMap<>();
        rows.stream().sorted(Comparator.comparing(FreshmanAdmissionRow::departmentName)
                .thenComparing(FreshmanAdmissionRow::majorName).thenComparing(FreshmanAdmissionRow::idDocumentNumber))
                .forEach(row -> groups.computeIfAbsent(row.departmentName() + "\u0000" + row.majorName(), ignored -> new ArrayList<>()).add(row));
        List<FreshmanClassAssignment> result = new ArrayList<>();
        groups.values().forEach(group -> result.addAll(assignMajor(group, enrollmentYear)));
        return List.copyOf(result);
    }

    private static List<FreshmanClassAssignment> assignMajor(List<FreshmanAdmissionRow> rows, int year) {
        int classCount = (rows.size() + MAX_CLASS_SIZE - 1) / MAX_CLASS_SIZE;
        List<Bucket> buckets = buckets(rows.size(), classCount);
        List<FreshmanAdmissionRow> males = rows.stream().filter(row -> row.gender().equals("男")).toList();
        List<FreshmanAdmissionRow> females = rows.stream().filter(row -> row.gender().equals("女")).toList();
        allocateMales(buckets, males.size(), rows.size());
        place(males, buckets, true);
        place(females, buckets, false);
        String suffix = String.format("%02d", year % 100);
        List<FreshmanClassAssignment> result = new ArrayList<>();
        for (Bucket bucket : buckets) for (FreshmanAdmissionRow row : bucket.rows) result.add(
                new FreshmanClassAssignment(row, bucket.number, row.majorName() + suffix
                        + String.format("%02d", bucket.number) + "班"));
        return result;
    }

    private static List<Bucket> buckets(int size, int count) {
        List<Bucket> result = new ArrayList<>();
        int base = size / count;
        int extra = size % count;
        for (int index = 0; index < count; index++) result.add(new Bucket(index + 1, base + (index < extra ? 1 : 0)));
        return result;
    }

    private static void allocateMales(List<Bucket> buckets, int males, int total) {
        int assigned = 0;
        for (Bucket bucket : buckets) { bucket.maleTarget = males * bucket.capacity / total; assigned += bucket.maleTarget; }
        for (int index = 0; assigned < males; index = (index + 1) % buckets.size()) {
            Bucket bucket = buckets.get(index);
            if (bucket.maleTarget < bucket.capacity) { bucket.maleTarget++; assigned++; }
        }
    }

    private static void place(List<FreshmanAdmissionRow> rows, List<Bucket> buckets, boolean male) {
        int index = 0;
        for (Bucket bucket : buckets) {
            int limit = male ? bucket.maleTarget : bucket.capacity - bucket.maleTarget;
            for (int placed = 0; placed < limit; placed++) bucket.rows.add(rows.get(index++));
        }
    }

    private static final class Bucket {
        private final int number;
        private final int capacity;
        private final List<FreshmanAdmissionRow> rows = new ArrayList<>();
        private int maleTarget;

        private Bucket(int number, int capacity) { this.number = number; this.capacity = capacity; }
    }
}
