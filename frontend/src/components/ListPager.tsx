import { Pagination } from "@mui/material";

export const ListPager = ({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) => {
  if (totalPages <= 1) return null;
  return (
    <Pagination
      count={totalPages}
      page={Math.min(page, Math.max(totalPages, 1))}
      onChange={(_event, next) => onChange(next)}
      siblingCount={2}
      boundaryCount={0}
      sx={{ alignSelf: "center" }}
    />
  );
};
