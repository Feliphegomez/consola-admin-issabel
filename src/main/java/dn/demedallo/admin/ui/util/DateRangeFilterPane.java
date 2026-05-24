package dn.demedallo.admin.ui.util;

import dn.demedallo.admin.util.ShiftDatetimeRange;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.time.LocalDate;

/**
 * Shared date range filter: preset periods (default today) or custom from/to pickers.
 */
public final class DateRangeFilterPane extends HBox {

    public enum Preset {
        TODAY("Hoy"),
        LAST_WEEK("Última semana"),
        LAST_15_DAYS("15 días"),
        MONTH("Mes"),
        YEAR("Año"),
        CUSTOM("Personalizado");

        private final String label;

        Preset(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final ComboBox<Preset> presetCombo = new ComboBox<>(
            FXCollections.observableArrayList(Preset.values()));
    private final DatePicker dateFrom = new DatePicker(LocalDate.now());
    private final DatePicker dateTo = new DatePicker(LocalDate.now());
    private final HBox customDates = new HBox(8);

    public DateRangeFilterPane() {
        this(true);
    }

    /** @param showPeriodLabel if true, prepends «Período:» */
    public DateRangeFilterPane(boolean showPeriodLabel) {
        getStyleClass().add("date-range-filter");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);

        presetCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Preset p) {
                return p == null ? "" : p.label();
            }

            @Override
            public Preset fromString(String s) {
                return null;
            }
        });
        presetCombo.setValue(Preset.TODAY);
        presetCombo.setOnAction(e -> applyPreset(presetCombo.getValue(), false));

        customDates.setAlignment(Pos.CENTER_LEFT);
        customDates.getChildren().addAll(
                new Label("Desde:"), dateFrom,
                new Label("Hasta:"), dateTo);
        customDates.setVisible(false);
        customDates.setManaged(false);

        if (showPeriodLabel) {
            getChildren().add(new Label("Período:"));
        }
        getChildren().addAll(presetCombo, customDates);
        applyPreset(Preset.TODAY, true);
    }

    public Preset selectedPreset() {
        return presetCombo.getValue();
    }

    public void selectPreset(Preset preset) {
        presetCombo.setValue(preset == null ? Preset.TODAY : preset);
        applyPreset(presetCombo.getValue(), true);
    }

    public void selectToday() {
        selectPreset(Preset.TODAY);
    }

    /** Switches to personalizado and sets both ends (e.g. drill-down on one day). */
    public void setCustomRange(LocalDate from, LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now();
        LocalDate t = to != null ? to : f;
        if (t.isBefore(f)) {
            LocalDate swap = f;
            f = t;
            t = swap;
        }
        dateFrom.setValue(f);
        dateTo.setValue(t);
        presetCombo.setValue(Preset.CUSTOM);
        applyPreset(Preset.CUSTOM, true);
    }

    public LocalDate getFrom() {
        return resolveFrom();
    }

    public LocalDate getTo() {
        return resolveTo();
    }

    public ShiftDatetimeRange toShiftRange() {
        return ShiftDatetimeRange.ofDates(getFrom(), getTo());
    }

    /** {@code null} if valid; otherwise Spanish error message. */
    public String validate() {
        LocalDate from = getFrom();
        LocalDate to = getTo();
        if (from == null || to == null) {
            return "Seleccione fechas válidas.";
        }
        return null;
    }

    public String periodLabel() {
        LocalDate from = getFrom();
        LocalDate to = getTo();
        if (from == null || to == null) {
            return "";
        }
        return from.equals(to) ? from.toString() : from + " — " + to;
    }

    private void applyPreset(Preset preset, boolean syncPickers) {
        if (preset == null) {
            preset = Preset.TODAY;
        }
        boolean custom = preset == Preset.CUSTOM;
        customDates.setVisible(custom);
        customDates.setManaged(custom);
        if (syncPickers && !custom) {
            LocalDate today = LocalDate.now();
            LocalDate from;
            LocalDate to = today;
            switch (preset) {
                case LAST_WEEK -> from = today.minusDays(6);
                case LAST_15_DAYS -> from = today.minusDays(14);
                case MONTH -> from = today.withDayOfMonth(1);
                case YEAR -> from = today.withDayOfYear(1);
                case TODAY -> from = today;
                default -> from = today;
            }
            dateFrom.setValue(from);
            dateTo.setValue(to);
        }
    }

    private LocalDate resolveFrom() {
        Preset p = presetCombo.getValue();
        if (p == Preset.CUSTOM) {
            return dateFrom.getValue();
        }
        LocalDate today = LocalDate.now();
        if (p == null || p == Preset.TODAY) {
            return today;
        }
        return switch (p) {
            case LAST_WEEK -> today.minusDays(6);
            case LAST_15_DAYS -> today.minusDays(14);
            case MONTH -> today.withDayOfMonth(1);
            case YEAR -> today.withDayOfYear(1);
            default -> today;
        };
    }

    private LocalDate resolveTo() {
        Preset p = presetCombo.getValue();
        if (p == Preset.CUSTOM) {
            return dateTo.getValue();
        }
        return LocalDate.now();
    }
}
