import { updateMyProfile } from '../api/generated/user-controller/user-controller';
import type { UpdateUserRequest, UserResponse } from '../api/generated/model';

export async function updateUserProfile(
  request: UpdateUserRequest,
): Promise<UserResponse> {
  const data = await updateMyProfile(request);
  return data as unknown as UserResponse;
}
