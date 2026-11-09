import { Button } from "@mui/material";
import { ArrowBack } from "@mui/icons-material";
import { useTranslation } from "react-i18next";

export const HomeworkBackButton = ({ onClick }: { onClick: () => void }) => {
  const { t } = useTranslation();
  return (
    <Button size="small" startIcon={<ArrowBack />} onClick={onClick} sx={{ mb: 1, alignSelf: "flex-start" }}>
      {t("homework.back")}
    </Button>
  );
};
