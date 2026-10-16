package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.util.*;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	static ResponseEntity<Object> json(HttpStatus status, Object body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    static ResponseEntity<Object> error(HttpStatus status, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg);
        return json(status, m);
    }

    // ---------- controller ----------
    @RestController
    @RequestMapping(value = "/api/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public static class EventController {

        private static final List<String> CATEGORIES =
                List.of("Music", "Tech", "Sports", "Workshop", "Festival");
        private static final List<String> SORT_FIELDS =
                List.of("title", "ticketPrice", "seatsAvailable");
        private static final List<String> FIELDS =
                List.of("title", "venue", "category", "ticketPrice", "seatsAvailable", "isOnline");

        private final List<Map<String, Object>> events = new ArrayList<>();
        private long nextId = 1;

        // Validates one field. Returns error message or null; puts cleaned value into out.
        private String checkField(String f, Object v, Map<String, Object> out) {
            switch (f) {
                case "title": {
                    if (!(v instanceof String)) return "title must be a string";
                    String t = ((String) v).trim();
                    if (t.length() < 3 || t.length() > 60) return "title must be 3 to 60 characters";
                    out.put(f, t);
                    return null;
                }
                case "venue": {
                    if (!(v instanceof String)) return "venue must be a string";
                    String t = ((String) v).trim();
                    if (t.length() < 2 || t.length() > 40) return "venue must be 2 to 40 characters";
                    out.put(f, t);
                    return null;
                }
                case "category": {
                    if (!(v instanceof String) || !CATEGORIES.contains(v))
                        return "category must be one of " + CATEGORIES;
                    out.put(f, v);
                    return null;
                }
                case "ticketPrice": {
                    if (!(v instanceof Number)) return "ticketPrice must be a number";
                    double d = ((Number) v).doubleValue();
                    if (Double.isNaN(d) || Double.isInfinite(d)) return "ticketPrice must be a number";
                    if (d <= 0) return "ticketPrice must be greater than 0";
                    if (d > 50000) return "ticketPrice must be at most 50000";
                    out.put(f, v);
                    return null;
                }
                case "seatsAvailable": {
                    if (!(v instanceof Number)) return "seatsAvailable must be an integer";
                    Number n = (Number) v;
                    double d = n.doubleValue();
                    if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.floor(d))
                        return "seatsAvailable must be an integer";
                    if (d < 0 || d > 100000) return "seatsAvailable must be 0 to 100000";
                    out.put(f, (long) d);
                    return null;
                }
                case "isOnline": {
                    if (!(v instanceof Boolean)) return "isOnline must be a boolean";
                    out.put(f, v);
                    return null;
                }
            }
            return "unknown field";
        }

        // full = all fields required (POST / PUT); otherwise PATCH rules
        @SuppressWarnings("unchecked")
        private String validate(Object body, boolean full, Map<String, Object> out) {
            if (!(body instanceof Map)) return "Body must be a JSON object";
            Map<String, Object> m = (Map<String, Object>) body;
            int count = 0;
            for (String f : FIELDS) {
                if (!m.containsKey(f) || m.get(f) == null) {
                    if (full) return f + " is required";
                    continue;
                }
                String err = checkField(f, m.get(f), out);
                if (err != null) return err;
                count++;
            }
            if (!full && count == 0) return "At least one valid field is required";
            return null;
        }

        private Map<String, Object> find(String id) {
            for (Map<String, Object> e : events) {
                if (String.valueOf(e.get("id")).equals(id)) return e;
            }
            return null;
        }

        private Map<String, Object> ordered(long id, Map<String, Object> data) {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("id", id);
            for (String f : FIELDS) rec.put(f, data.get(f));
            return rec;
        }

        // ---------- CREATE ----------
        @PostMapping
        public synchronized ResponseEntity<Object> create(@RequestBody(required = false) Object body) {
            Map<String, Object> data = new HashMap<>();
            String err = validate(body, true, data);
            if (err != null) return error(HttpStatus.BAD_REQUEST, err);
            Map<String, Object> rec = ordered(nextId++, data);
            events.add(rec);
            return json(HttpStatus.CREATED, rec);
        }

        // ---------- READ ONE ----------
        @GetMapping("/{id}")
        public synchronized ResponseEntity<Object> getOne(@PathVariable String id) {
            Map<String, Object> e = find(id);
            if (e == null) return error(HttpStatus.NOT_FOUND, "Record not found");
            return json(HttpStatus.OK, e);
        }

        // ---------- PUT ----------
        @PutMapping("/{id}")
        public synchronized ResponseEntity<Object> replace(@PathVariable String id,
                                                           @RequestBody(required = false) Object body) {
            Map<String, Object> e = find(id);
            if (e == null) return error(HttpStatus.NOT_FOUND, "Record not found");
            Map<String, Object> data = new HashMap<>();
            String err = validate(body, true, data);
            if (err != null) return error(HttpStatus.BAD_REQUEST, err);
            e.putAll(data);
            return json(HttpStatus.OK, e);
        }

        // ---------- PATCH ----------
        @PatchMapping("/{id}")
        public synchronized ResponseEntity<Object> patch(@PathVariable String id,
                                                         @RequestBody(required = false) Object body) {
            Map<String, Object> e = find(id);
            if (e == null) return error(HttpStatus.NOT_FOUND, "Record not found");
            Map<String, Object> data = new HashMap<>();
            String err = validate(body, false, data);
            if (err != null) return error(HttpStatus.BAD_REQUEST, err);
            e.putAll(data);
            return json(HttpStatus.OK, e);
        }

        // ---------- DELETE ----------
        @DeleteMapping("/{id}")
        public synchronized ResponseEntity<Object> delete(@PathVariable String id) {
            Map<String, Object> e = find(id);
            if (e == null) return error(HttpStatus.NOT_FOUND, "Record not found");
            events.remove(e);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }

        // ---------- LIST ----------
        private static Double parseNum(String s) {
            try {
                return new BigDecimal(s.trim()).doubleValue();
            } catch (Exception ex) {
                return null;
            }
        }

        private static long parseLong(String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ex) {
                return Long.MAX_VALUE; // digits only but too big
            }
        }

        @GetMapping
        public synchronized ResponseEntity<Object> list(HttpServletRequest req) {
            Map<String, String> q = new HashMap<>();
            for (Map.Entry<String, String[]> en : req.getParameterMap().entrySet()) {
                if (en.getValue().length != 1) return error(HttpStatus.BAD_REQUEST, "Invalid query parameter: " + en.getKey());
                q.put(en.getKey(), en.getValue()[0]);
            }

            long page = 1;
            if (q.containsKey("page")) {
                String s = q.get("page");
                if (!s.matches("\\d+")) return error(HttpStatus.BAD_REQUEST, "Invalid page");
                page = parseLong(s);
                if (page < 1) return error(HttpStatus.BAD_REQUEST, "Invalid page");
            }
            long limit = 10;
            if (q.containsKey("limit")) {
                String s = q.get("limit");
                if (!s.matches("\\d+")) return error(HttpStatus.BAD_REQUEST, "Invalid limit");
                limit = parseLong(s);
                if (limit < 1 || limit > 50) return error(HttpStatus.BAD_REQUEST, "limit must be 1 to 50");
            }

            String category = q.get("category");
            if (category != null && !CATEGORIES.contains(category))
                return error(HttpStatus.BAD_REQUEST, "Invalid category");

            Boolean isOnline = null;
            if (q.containsKey("isOnline")) {
                String s = q.get("isOnline");
                if (s.equals("true")) isOnline = true;
                else if (s.equals("false")) isOnline = false;
                else return error(HttpStatus.BAD_REQUEST, "Invalid isOnline");
            }

            Double minP = null, maxP = null;
            if (q.containsKey("minTicketPrice")) {
                minP = parseNum(q.get("minTicketPrice"));
                if (minP == null) return error(HttpStatus.BAD_REQUEST, "Invalid minTicketPrice");
            }
            if (q.containsKey("maxTicketPrice")) {
                maxP = parseNum(q.get("maxTicketPrice"));
                if (maxP == null) return error(HttpStatus.BAD_REQUEST, "Invalid maxTicketPrice");
            }

            String sort = q.get("sort");
            if (sort != null && !SORT_FIELDS.contains(sort))
                return error(HttpStatus.BAD_REQUEST, "Invalid sort field");
            String order = "asc";
            if (q.containsKey("order")) {
                order = q.get("order");
                if (!order.equals("asc") && !order.equals("desc"))
                    return error(HttpStatus.BAD_REQUEST, "Invalid order");
            }

            // filters
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map<String, Object> e : events) {
                if (category != null && !category.equals(e.get("category"))) continue;
                if (isOnline != null && !isOnline.equals(e.get("isOnline"))) continue;
                double price = ((Number) e.get("ticketPrice")).doubleValue();
                if (minP != null && price < minP) continue;
                if (maxP != null && price > maxP) continue;
                list.add(e);
            }

            // search
            if (q.containsKey("search")) {
                String s = q.get("search").trim().toLowerCase();
                if (!s.isEmpty()) {
                    List<Map<String, Object>> res = new ArrayList<>();
                    for (Map<String, Object> e : list) {
                        if (((String) e.get("title")).toLowerCase().contains(s)
                                || ((String) e.get("venue")).toLowerCase().contains(s)) res.add(e);
                    }
                    list = res;
                }
            }

            // sort (List.sort is stable)
            if (sort != null) {
                final String sf = sort;
                Comparator<Map<String, Object>> cmp;
                if (sf.equals("title")) {
                    cmp = Comparator.comparing(e -> ((String) e.get("title")).toLowerCase());
                } else {
                    cmp = Comparator.comparingDouble(e -> ((Number) e.get(sf)).doubleValue());
                }
                if (order.equals("desc")) cmp = cmp.reversed();
                list.sort(cmp);
            }

            // pagination
            int total = list.size();
            long totalPages = total == 0 ? 0 : (total + limit - 1) / limit;
            List<Map<String, Object>> data = new ArrayList<>();
            if (page <= totalPages) {
                int start = (int) ((page - 1) * limit);
                int end = (int) Math.min((long) total, start + limit);
                data = list.subList(start, end);
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("data", data);
            resp.put("page", page);
            resp.put("limit", limit);
            resp.put("total", total);
            resp.put("totalPages", totalPages);
            return json(HttpStatus.OK, resp);
        }
    }

    // ---------- error handling: always JSON ----------
    @RestControllerAdvice
    public static class Errors {
        @ExceptionHandler({
                org.springframework.http.converter.HttpMessageNotReadableException.class,
                org.springframework.web.HttpMediaTypeNotSupportedException.class,
                org.springframework.web.bind.MissingServletRequestParameterException.class,
                org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
        })
        public ResponseEntity<Object> badRequest(Exception e) {
            return error(HttpStatus.BAD_REQUEST, "Invalid request");
        }

        @ExceptionHandler({
                org.springframework.web.servlet.resource.NoResourceFoundException.class,
                org.springframework.web.servlet.NoHandlerFoundException.class,
                org.springframework.web.HttpRequestMethodNotSupportedException.class,
                org.springframework.web.HttpMediaTypeNotAcceptableException.class
        })
        public ResponseEntity<Object> notFound(Exception e) {
            return error(HttpStatus.NOT_FOUND, "Route not found");
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Object> other(Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }
}
