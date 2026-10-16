package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class DemoApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void combinesSearchFiltersSortingAndPagination() throws Exception {
		mockMvc.perform(get("/api/items")
						.param("search", "tech")
						.param("category", "Conference")
						.param("minPrice", "100")
						.param("maxPrice", "1000")
						.param("sortBy", "price")
						.param("direction", "asc")
						.param("page", "0")
						.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].name").value("Chennai Tech Summit"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void rejectsUnsupportedSortFields() throws Exception {
		mockMvc.perform(get("/api/items").param("sortBy", "unknown"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsInvalidPaginationValues() throws Exception {
		mockMvc.perform(get("/api/items").param("page", "-1"))
				.andExpect(status().isBadRequest());
	}

}
