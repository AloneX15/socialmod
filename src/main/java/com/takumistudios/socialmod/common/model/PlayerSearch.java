package com.takumistudios.socialmod.common.model;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Search only public identities; callers supply presence already filtered for the viewer. */
public final class PlayerSearch {
    public static final int PAGE_SIZE = 20;
    public record Entry(UUID id, String name, String status) { }
    public record Result(int request, int page, boolean more, List<Entry> entries) { }
    public static Result find(int request, String query, int page, UUID self, List<Entry> candidates) {
        if (request < 0 || query == null || query.length() > 32 || page < 0 || page > 10000) throw new IllegalArgumentException("Invalid search");
        String filter = query.strip().toLowerCase(Locale.ROOT);
        var matches = candidates.stream().filter(e -> !e.id().equals(self))
            .filter(e -> filter.length() >= 2 || !e.status().equals("offline"))
            .filter(e -> e.name().toLowerCase(Locale.ROOT).contains(filter))
            .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id)).toList();
        long offset = (long) page * PAGE_SIZE;
        return new Result(request, page, offset + PAGE_SIZE < matches.size(), matches.stream().skip(offset).limit(PAGE_SIZE).toList());
    }
    private PlayerSearch() { }
}
