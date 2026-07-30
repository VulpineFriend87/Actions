package top.vulpine.actions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.vulpine.actions.action.ActionContext;
import top.vulpine.actions.condition.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionTest {

    /** Deterministic stand-ins, so boolean logic can be tested without a server. */
    private static final String TRUE = "chance: 100%";
    private static final String FALSE = "chance: 0%";

    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        warnings.clear();
        Actions.logger(warnings::add);
        Actions.placeholders(null);
    }

    private ActionContext context() {
        return ActionContext.builder(new FakeScheduler())
                .value("level", 12)
                .value("balance", 2500.5)
                .value("world", "lobby")
                .value("name", "VulpineFriend87")
                .build();
    }

    private boolean test(final Object node) {
        return Condition.parse(node).test(context());
    }

    // --- comparison operators -------------------------------------------------

    @Test
    @DisplayName("numeric comparisons")
    void numeric() {
        assertTrue(test("%level% == 12"));
        assertTrue(test("%level% != 11"));
        assertTrue(test("%level% > 10"));
        assertTrue(test("%level% >= 12"));
        assertTrue(test("%level% < 20"));
        assertTrue(test("%level% <= 12"));
        assertFalse(test("%level% > 12"));
        assertTrue(test("%balance% >= 1000"));
    }

    @Test
    @DisplayName(">= is not read as >")
    void longestOperatorWins() {
        // If '>' matched first, the right side would be "= 12" and parsing would fail.
        assertTrue(test("%level% >= 12"));
        assertTrue(test("%level% <= 12"));
    }

    @Test
    @DisplayName("text comparisons, case-insensitive")
    void textual() {
        assertTrue(test("%world% == LOBBY"));
        assertTrue(test("%world% != arena"));
        assertTrue(test("%name% contains vulpine"));
        assertTrue(test("%name% starts_with Vulpine"));
        assertTrue(test("%name% ends_with 87"));
    }

    @Test
    @DisplayName("matches is a regex and stays case-sensitive")
    void regex() {
        assertTrue(test("%name% matches ^Vulpine[A-Za-z]+[0-9]+$"));
        assertFalse(test("%name% matches ^vulpine.*$"));
        assertTrue(test("%name% matches (?i)^vulpine.*$"));
    }

    @Test
    @DisplayName("numbers compare as numbers, not as text")
    void numericNotLexical() {
        // Lexically "9" > "10"; numerically it is not. Getting this wrong is the
        // classic silent bug in config-driven comparisons.
        assertTrue(Condition.parse("%n% > 9").test(
                ActionContext.builder(new FakeScheduler()).value("n", 10).build()));
    }

    @Test
    @DisplayName("ordering non-numbers is refused rather than sorted alphabetically")
    void orderingNonNumeric() {
        assertFalse(test("%world% > arena"));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("non-numeric")), warnings.toString());
    }

    // --- named checks ---------------------------------------------------------

    @Test
    @DisplayName("chance accepts both 25% and 0.25")
    void chance() {
        assertTrue(test("chance: 100%"));
        assertFalse(test("chance: 0%"));
        assertTrue(test("chance: 1.0"));
        assertFalse(test("chance: 0"));
    }

    @Test
    @DisplayName("player checks are false without a player rather than throwing")
    void playerChecksWithoutPlayer() {
        assertFalse(test("permission: lobby.vip"));
        assertFalse(test("world: lobby"));
        assertFalse(test("gamemode: creative"));
        assertFalse(test("sneaking: true"));
    }

    // --- boolean structure ----------------------------------------------------

    @Test
    @DisplayName("a list means all of them")
    void listIsAll() {
        assertTrue(test(List.of(TRUE, TRUE)));
        assertFalse(test(List.of(TRUE, FALSE)));
    }

    @Test
    @DisplayName("all and any")
    void allAndAny() {
        assertTrue(test(Map.of("all", List.of(TRUE, TRUE))));
        assertFalse(test(Map.of("all", List.of(TRUE, FALSE))));
        assertTrue(test(Map.of("any", List.of(FALSE, TRUE))));
        assertFalse(test(Map.of("any", List.of(FALSE, FALSE))));
    }

    @Test
    @DisplayName("not inverts")
    void not() {
        assertTrue(test(Map.of("not", FALSE)));
        assertFalse(test(Map.of("not", TRUE)));
    }

    @Test
    @DisplayName("keys side by side combine with AND")
    void siblingKeysAreAnded() {
        // any:[false,true] AND not:false  ->  true AND true
        assertTrue(test(Map.of("any", List.of(FALSE, TRUE), "not", FALSE)));

        // any:[false,true] AND not:true   ->  true AND false
        assertFalse(test(Map.of("any", List.of(FALSE, TRUE), "not", TRUE)));

        // any:[false,false] AND not:false ->  false AND true
        assertFalse(test(Map.of("any", List.of(FALSE, FALSE), "not", FALSE)));
    }

    @Test
    @DisplayName("blocks nest inside any and all, to any depth")
    void nesting() {
        // any: [ FALSE, not: FALSE ]  ->  false OR true
        assertTrue(test(Map.of("any", List.of(FALSE, Map.of("not", FALSE)))));

        // any: [ FALSE, all: [TRUE, FALSE] ]  ->  false OR false
        assertFalse(test(Map.of("any", List.of(FALSE, Map.of("all", List.of(TRUE, FALSE))))));

        // all: [ any:[FALSE,TRUE], not: { any: [FALSE, FALSE] } ]  ->  true AND true
        assertTrue(test(Map.of("all", List.of(
                Map.of("any", List.of(FALSE, TRUE)),
                Map.of("not", Map.of("any", List.of(FALSE, FALSE)))))));
    }

    @Test
    @DisplayName("an absent condition is always true")
    void absent() {
        assertTrue(Condition.ALWAYS.test(context()));
        assertTrue(test(null));
    }

    // --- failure modes --------------------------------------------------------

    @Test
    @DisplayName("an unresolved placeholder is false and warns once, not every call")
    void unresolvedPlaceholder() {

        ActionContext bare = ActionContext.builder(new FakeScheduler()).build();
        Condition condition = Condition.parse("%player_level% >= 10");

        assertFalse(condition.test(bare));
        assertFalse(condition.test(bare));
        assertFalse(condition.test(bare));

        assertTrue(warnings.stream().anyMatch(w -> w.contains("unresolved placeholder")), warnings.toString());
        assertEquals1(warnings.stream().filter(w -> w.contains("unresolved placeholder")).count());
    }

    private static void assertEquals1(final long count) {
        org.junit.jupiter.api.Assertions.assertEquals(1L, count,
                "a per-join warning must not repeat forever");
    }

    @Test
    @DisplayName("not with a list is refused instead of guessing")
    void notWithListIsRefused() {
        assertFalse(test(Map.of("not", List.of(TRUE, FALSE))));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("single condition")), warnings.toString());
    }

    @Test
    @DisplayName("nonsense warns and is false")
    void nonsense() {
        assertFalse(test("this is not a condition"));
        assertFalse(test(Map.of("wat", List.of(TRUE))));
        assertFalse(test(""));
        assertTrue(warnings.size() >= 3, warnings.toString());
    }

    @Test
    @DisplayName("registered placeholder expansion is applied after context values")
    void externalPlaceholders() {

        Actions.placeholders((player, text) -> text.replace("%external%", "42"));

        assertTrue(test("%external% == 42"));
        assertTrue(test("%level% == 12"), "context values must still win");
    }
}
