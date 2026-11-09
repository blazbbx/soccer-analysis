import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import { DateTimePicker } from "@mui/x-date-pickers/DateTimePicker";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { huHU } from "@mui/x-date-pickers/locales";
import dayjs from "dayjs";
import "dayjs/locale/hu";
import { useTranslation } from "react-i18next";

/** The app's themed picker, not the browser's: the native calendar icon stays black on a dark background. */
export const HomeworkDateTimeField = ({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) => {
  const { i18n } = useTranslation();
  const hungarian = i18n.language.toLowerCase().startsWith("hu");
  return (
  <LocalizationProvider
    dateAdapter={AdapterDayjs}
    adapterLocale={hungarian ? "hu" : "en"}
    localeText={hungarian ? huHU.components.MuiLocalizationProvider.defaultProps.localeText : undefined}
  >
  <DateTimePicker
    label={label}
    ampm={!hungarian}
    format={hungarian ? "YYYY.MM.DD. HH:mm" : "MMM D, YYYY hh:mm A"}
    value={value ? dayjs(value) : null}
    onChange={(next) => {
      // A half-typed date is not passed on, or the field would lose what is being typed.
      if (next == null) onChange("");
      else if (next.isValid()) onChange(next.format("YYYY-MM-DDTHH:mm"));
    }}
    slotProps={{ textField: { size: "small", sx: { minWidth: 240 } }, field: { clearable: true } }}
  />
  </LocalizationProvider>
  );
};
