package com.huit.library.modules.inventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class IsbnTest {
    @Test void acceptsCheckedIsbn10And13AndConvertsEquivalentEditionIdentifiers() {
        assertThat(Isbn.canonical(" 0-13-235088-2 ")).isEqualTo("9780132350884");
        assertThat(Isbn.canonical("978 0 13 235088 4")).isEqualTo("9780132350884");
        assertThat(Isbn.canonical("0-8044-2957-x")).isEqualTo("9780804429573");
        assertThat(Isbn.legacy("9780132350884")).isEqualTo("0132350882");
        assertThat(Isbn.legacy("9791090636071")).isEmpty();
    }
    @Test void rejectsInvalidShapePrefixAndCheckDigitsRatherThanGuessingMetadata() {
        for (String value : new String[] { "", "abc9780132350884", "9780132350885", "0132350881", "9770132350884", "123", "978/0132350884" }) {
            assertThatThrownBy(() -> Isbn.canonical(value)).as(value).isInstanceOf(ResponseStatusException.class);
        }
    }
}
