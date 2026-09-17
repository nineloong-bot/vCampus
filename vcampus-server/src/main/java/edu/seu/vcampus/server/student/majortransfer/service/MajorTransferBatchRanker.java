package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ranks assessed transfer applications by scores and partitions them by target option quota. */
final class MajorTransferBatchRanker {

    record Result(
            List<MajorTransferRepository.ApplicationRow> approved,
            List<MajorTransferRepository.ApplicationRow> rejected
    ) { }

    Result rank(List<MajorTransferRepository.ApplicationRow> applications,
                 Map<String, MajorTransferRepository.OptionRow> options) {
        List<MajorTransferRepository.ApplicationRow> approved = new ArrayList<>();
        List<MajorTransferRepository.ApplicationRow> rejected = new ArrayList<>();

        Map<String, List<MajorTransferRepository.ApplicationRow>> grouped = new HashMap<>();
        for (var app : applications) {
            grouped.computeIfAbsent(app.optionId(), k -> new ArrayList<>()).add(app);
        }

        Comparator<MajorTransferRepository.ApplicationRow> scoreComparator = (a, b) -> {
            int cmp = compareDouble(b.finalScore(), a.finalScore());
            if (cmp != 0) return cmp;
            cmp = compareDouble(b.writtenScore(), a.writtenScore());
            if (cmp != 0) return cmp;
            cmp = compareDouble(b.interviewScore(), a.interviewScore());
            if (cmp != 0) return cmp;
            return a.applicationId().compareTo(b.applicationId());
        };

        for (var entry : grouped.entrySet()) {
            var list = new ArrayList<>(entry.getValue());
            list.sort(scoreComparator);
            var option = options.get(entry.getKey());
            int quota = option != null ? Math.max(0, option.receiveQuota()) : 0;
            int cutoff = Math.min(list.size(), quota);

            approved.addAll(list.subList(0, cutoff));
            rejected.addAll(list.subList(cutoff, list.size()));
        }

        return new Result(approved, rejected);
    }

    private static int compareDouble(Double d1, Double d2) {
        if (d1 == null && d2 == null) return 0;
        if (d1 == null) return -1;
        if (d2 == null) return 1;
        return Double.compare(d1, d2);
    }
}
