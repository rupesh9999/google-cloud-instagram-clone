import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from 'react-query';
import { ArrowLeftIcon, CameraIcon } from '@heroicons/react/24/outline';
import { userService, UpdateProfileRequest } from '../services/userService';
import { useAuthStore } from '../hooks/useAuthStore';
import LoadingSpinner from '../components/LoadingSpinner';
import toast from 'react-hot-toast';

const EditProfile: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user, setUser } = useAuthStore();

  const [fullName, setFullName] = useState(user?.fullName || '');
  const [bio, setBio] = useState(user?.bio || '');
  const [profilePicture, setProfilePicture] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  const updateProfileMutation = useMutation(
    (data: UpdateProfileRequest) => userService.updateProfile(data),
    {
      onSuccess: (updatedUser) => {
        setUser(updatedUser);
        queryClient.invalidateQueries(['user', user?.username]);
        toast.success('Profile updated successfully!');
        navigate(`/profile/${updatedUser.username}`);
      },
      onError: () => {
        toast.error('Failed to update profile');
      },
    }
  );

  const handleImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (file.size > 5 * 1024 * 1024) {
        toast.error('Image must be less than 5MB');
        return;
      }
      setProfilePicture(file);
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    const data: UpdateProfileRequest = {};
    if (fullName !== user?.fullName) data.fullName = fullName;
    if (bio !== user?.bio) data.bio = bio;
    if (profilePicture) data.profilePicture = profilePicture;

    if (Object.keys(data).length === 0) {
      toast('No changes to save');
      return;
    }

    updateProfileMutation.mutate(data);
  };

  return (
    <div className="mt-16 max-w-2xl mx-auto">
      <div className="bg-white border border-gray-200 rounded-lg">
        {/* Header */}
        <div className="flex items-center gap-4 p-4 border-b border-gray-200">
          <button
            onClick={() => navigate(-1)}
            className="p-2 hover:bg-gray-100 rounded-full"
          >
            <ArrowLeftIcon className="h-6 w-6" />
          </button>
          <h1 className="text-lg font-semibold">Edit profile</h1>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-6">
          {/* Profile Picture */}
          <div className="flex items-center gap-6">
            <div className="relative">
              <img
                src={
                  previewUrl ||
                  user?.profilePictureUrl ||
                  `https://ui-avatars.com/api/?name=${user?.username}&background=random&size=80`
                }
                alt={user?.username}
                className="w-20 h-20 rounded-full object-cover"
              />
              <label
                htmlFor="profile-picture"
                className="absolute bottom-0 right-0 p-1.5 bg-blue-500 rounded-full cursor-pointer hover:bg-blue-600 transition-colors"
              >
                <CameraIcon className="h-4 w-4 text-white" />
                <input
                  type="file"
                  id="profile-picture"
                  accept="image/*"
                  onChange={handleImageChange}
                  className="hidden"
                />
              </label>
            </div>
            <div>
              <p className="font-semibold">{user?.username}</p>
              <label
                htmlFor="profile-picture"
                className="text-sm text-blue-500 font-semibold cursor-pointer hover:text-blue-600"
              >
                Change profile photo
              </label>
            </div>
          </div>

          {/* Full Name */}
          <div>
            <label className="block text-sm font-semibold mb-2">Name</label>
            <input
              type="text"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Name"
              className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-lg focus:outline-none focus:border-gray-400"
              maxLength={50}
            />
            <p className="text-xs text-gray-500 mt-1">
              Help people discover your account by using the name you're known by.
            </p>
          </div>

          {/* Bio */}
          <div>
            <label className="block text-sm font-semibold mb-2">Bio</label>
            <textarea
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              placeholder="Bio"
              rows={4}
              className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-lg focus:outline-none focus:border-gray-400 resize-none"
              maxLength={150}
            />
            <p className="text-xs text-gray-500 mt-1">
              {bio.length}/150
            </p>
          </div>

          {/* Submit Button */}
          <button
            type="submit"
            disabled={updateProfileMutation.isLoading}
            className="w-full py-3 gradient-btn text-white font-semibold rounded-lg disabled:opacity-50"
          >
            {updateProfileMutation.isLoading ? (
              <LoadingSpinner size="sm" />
            ) : (
              'Submit'
            )}
          </button>
        </form>
      </div>
    </div>
  );
};

export default EditProfile;
