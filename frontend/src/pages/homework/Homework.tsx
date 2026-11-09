import { useView } from "../../context/ViewContext";
import { ROLES } from "../../types/roles";
import { MyHomeworkList } from "./assignee/MyHomeworkList";
import { HomeworkList } from "./coach/HomeworkList";

export const Homework = () => {
  const { activeView } = useView();
  const recipient = activeView === ROLES.PLAYER || activeView === ROLES.PARENT;
  return recipient ? <MyHomeworkList /> : <HomeworkList />;
};
