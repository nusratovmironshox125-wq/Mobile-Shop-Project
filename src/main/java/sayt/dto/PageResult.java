package sayt.dto;

import java.util.List;
public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {
    public static <T> PageResult<T> of(List<T> items, int page, long total) {
        return new PageResult<>(items, page, 10, total, (int)((total + 9) / 10), (page + 1L) * 10 < total);
    }
}
