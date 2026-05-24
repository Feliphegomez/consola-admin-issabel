package dn.demedallo.admin.ui.pbx;

import dn.demedallo.admin.util.FreePbxTimeRuleCodec;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Structured Issabel/FreePBX time slot editor (matches web GUI fields).
 */
public final class TimeGroupDetailFormPane extends VBox {

    private final Label preview = new Label();
    private final TextField name = new TextField();
    private final ComboBox<String> startHour = hourCombo();
    private final ComboBox<String> startMinute = minuteCombo();
    private final ComboBox<String> endHour = hourCombo();
    private final ComboBox<String> endMinute = minuteCombo();
    private final ComboBox<String> weekdayStart = weekdayCombo();
    private final ComboBox<String> weekdayEnd = weekdayCombo();
    private final ComboBox<String> monthDayStart = monthDayCombo();
    private final ComboBox<String> monthDayEnd = monthDayCombo();
    private final ComboBox<String> monthStart = monthCombo();
    private final ComboBox<String> monthEnd = monthCombo();
    private final Label generatedRule = new Label();

    private final ChangeListener<Object> refreshListener = (o, a, b) -> refreshPreview();

    public TimeGroupDetailFormPane(String existingName, String existingRule) {
        getStyleClass().add("pbx-time-detail-form");
        setSpacing(8);
        preview.getStyleClass().add("pbx-time-preview");
        preview.setMaxWidth(Double.MAX_VALUE);
        preview.setWrapText(true);
        generatedRule.getStyleClass().add("panel-hint");
        generatedRule.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(4, 0, 0, 0));

        int row = 0;
        addRow(grid, row++, "Nombre", name);
        addRow(grid, row++, "Hora de inicio", timePicker(startHour, startMinute));
        addRow(grid, row++, "Hora de fin", timePicker(endHour, endMinute));
        addRow(grid, row++, "Día de la semana de inicio", weekdayStart);
        addRow(grid, row++, "Día de la semana de fin", weekdayEnd);
        addRow(grid, row++, "Día del mes de inicio", monthDayStart);
        addRow(grid, row++, "Día del mes de fin", monthDayEnd);
        addRow(grid, row++, "Mes de inicio", monthStart);
        addRow(grid, row++, "Mes de fin", monthEnd);

        getChildren().addAll(preview, grid, generatedRule);
        attachListeners();
        load(existingName, existingRule);
    }

    public String nameValue() {
        return name.getText() == null ? "" : name.getText().trim();
    }

    public String buildTimeRule() {
        FreePbxTimeRuleCodec.TimeRuleParts parts = currentParts();
        FreePbxTimeRuleCodec.validate(parts);
        return FreePbxTimeRuleCodec.build(parts);
    }

    private void load(String existingName, String existingRule) {
        name.setText(existingName == null ? "" : existingName);
        FreePbxTimeRuleCodec.TimeRuleParts parts;
        try {
            parts = FreePbxTimeRuleCodec.parse(existingRule == null ? "" : existingRule);
        } catch (IllegalArgumentException ex) {
            parts = FreePbxTimeRuleCodec.TimeRuleParts.defaults();
            generatedRule.setText("Regla anterior no parseable; revise los campos. "
                    + (existingRule == null ? "" : existingRule));
        }
        select(startHour, parts.startHour());
        select(startMinute, parts.startMinute());
        select(endHour, parts.endHour());
        select(endMinute, parts.endMinute());
        selectWeekday(weekdayStart, parts.weekdayStart());
        selectWeekday(weekdayEnd, parts.weekdayEnd());
        selectMonthDay(monthDayStart, parts.monthDayStart());
        selectMonthDay(monthDayEnd, parts.monthDayEnd());
        selectMonth(monthStart, parts.monthStart());
        selectMonth(monthEnd, parts.monthEnd());
        refreshPreview();
    }

    private FreePbxTimeRuleCodec.TimeRuleParts currentParts() {
        return new FreePbxTimeRuleCodec.TimeRuleParts(
                value(startHour),
                value(startMinute),
                value(endHour),
                value(endMinute),
                code(weekdayStart),
                code(weekdayEnd),
                normalizeSegment(code(monthDayStart)),
                normalizeSegment(code(monthDayEnd)),
                normalizeSegment(code(monthStart)),
                normalizeSegment(code(monthEnd)));
    }

    private static String normalizeSegment(String code) {
        if (FreePbxTimeRuleCodec.ANY_DAY.equals(code)) {
            return FreePbxTimeRuleCodec.ANY;
        }
        return code;
    }

    private void refreshPreview() {
        try {
            FreePbxTimeRuleCodec.TimeRuleParts parts = currentParts();
            FreePbxTimeRuleCodec.validate(parts);
            String rule = FreePbxTimeRuleCodec.build(parts);
            preview.setText(parts.preview(nameValue()));
            generatedRule.setText("Regla generada: " + rule);
        } catch (Exception ex) {
            preview.setText(nameValue().isBlank() ? "Franja horaria" : nameValue());
            generatedRule.setText(ex.getMessage() == null ? "Complete los campos." : ex.getMessage());
        }
    }

    private void attachListeners() {
        for (ComboBox<String> combo : List.of(
                startHour, startMinute, endHour, endMinute,
                weekdayStart, weekdayEnd, monthDayStart, monthDayEnd, monthStart, monthEnd)) {
            combo.valueProperty().addListener(refreshListener);
        }
        name.textProperty().addListener((o, a, b) -> refreshPreview());
    }

    private static HBox timePicker(ComboBox<String> hour, ComboBox<String> minute) {
        Label colon = new Label(":");
        HBox box = new HBox(6, hour, colon, minute);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static ComboBox<String> hourCombo() {
        List<String> items = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            items.add(String.format(Locale.ROOT, "%02d", h));
        }
        return sizedCombo(items);
    }

    private static ComboBox<String> minuteCombo() {
        List<String> items = new ArrayList<>();
        for (int m = 0; m < 60; m++) {
            items.add(String.format(Locale.ROOT, "%02d", m));
        }
        return sizedCombo(items);
    }

    private static ComboBox<String> weekdayCombo() {
        List<String> items = new ArrayList<>();
        items.add("Cualquiera|" + FreePbxTimeRuleCodec.ANY);
        for (Map.Entry<String, String> e : FreePbxTimeRuleCodec.weekdayLabels().entrySet()) {
            items.add(e.getValue() + "|" + e.getKey());
        }
        return sizedCombo(items);
    }

    private static ComboBox<String> monthDayCombo() {
        List<String> items = new ArrayList<>();
        items.add("—|" + FreePbxTimeRuleCodec.ANY_DAY);
        for (int d = 1; d <= 31; d++) {
            items.add(String.valueOf(d) + "|" + d);
        }
        return sizedCombo(items);
    }

    private static ComboBox<String> monthCombo() {
        List<String> items = new ArrayList<>();
        items.add("—|" + FreePbxTimeRuleCodec.ANY_DAY);
        for (Map.Entry<String, String> e : FreePbxTimeRuleCodec.monthLabels().entrySet()) {
            items.add(e.getValue() + "|" + e.getKey());
        }
        return sizedCombo(items);
    }

    private static ComboBox<String> sizedCombo(List<String> items) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll(items);
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    private static void addRow(GridPane grid, int row, String label, javafx.scene.Node control) {
        Label l = new Label(label);
        grid.add(l, 0, row);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private static void select(ComboBox<String> combo, String code) {
        if (code == null) {
            return;
        }
        for (String item : combo.getItems()) {
            if (item.endsWith("|" + code)) {
                combo.getSelectionModel().select(item);
                return;
            }
        }
        if (!combo.getItems().isEmpty()) {
            combo.getSelectionModel().selectFirst();
        }
    }

    private static void selectWeekday(ComboBox<String> combo, String code) {
        if (FreePbxTimeRuleCodec.ANY.equals(code)) {
            select(combo, FreePbxTimeRuleCodec.ANY);
            return;
        }
        select(combo, code);
    }

    private static void selectMonthDay(ComboBox<String> combo, String code) {
        if (FreePbxTimeRuleCodec.ANY.equals(code) || FreePbxTimeRuleCodec.ANY_DAY.equals(code)) {
            select(combo, FreePbxTimeRuleCodec.ANY_DAY);
            return;
        }
        select(combo, code);
    }

    private static void selectMonth(ComboBox<String> combo, String code) {
        if (FreePbxTimeRuleCodec.ANY.equals(code) || FreePbxTimeRuleCodec.ANY_DAY.equals(code)) {
            select(combo, FreePbxTimeRuleCodec.ANY_DAY);
            return;
        }
        select(combo, code);
    }

    private static String value(ComboBox<String> combo) {
        String item = combo.getSelectionModel().getSelectedItem();
        if (item == null || !item.contains("|")) {
            return "";
        }
        return item.substring(item.indexOf('|') + 1);
    }

    private static String code(ComboBox<String> combo) {
        return value(combo);
    }
}
