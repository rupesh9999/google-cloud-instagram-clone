import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from 'react-query';
import { ArrowLeftIcon, PhotoIcon, XMarkIcon } from '@heroicons/react/24/outline';
import { postService, CreatePostRequest } from '../services/postService';
import LoadingSpinner from '../components/LoadingSpinner';
import toast from 'react-hot-toast';

const CreatePost: React.FC = () => {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [image, setImage] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [caption, setCaption] = useState('');
  const [hashtagInput, setHashtagInput] = useState('');
  const [hashtags, setHashtags] = useState<string[]>([]);

  const createPostMutation = useMutation(
    (data: CreatePostRequest) => postService.createPost(data),
    {
      onSuccess: (post) => {
        toast.success('Post created successfully!');
        navigate(`/post/${post.id}`);
      },
      onError: () => {
        toast.error('Failed to create post');
      },
    }
  );

  const handleImageSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (file.size > 10 * 1024 * 1024) {
        toast.error('Image must be less than 10MB');
        return;
      }
      setImage(file);
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  const handleAddHashtag = () => {
    const tag = hashtagInput.trim().toLowerCase().replace(/[^a-z0-9]/g, '');
    if (tag && !hashtags.includes(tag) && hashtags.length < 30) {
      setHashtags([...hashtags, tag]);
      setHashtagInput('');
    }
  };

  const handleRemoveHashtag = (tag: string) => {
    setHashtags(hashtags.filter((t) => t !== tag));
  };

  const handleHashtagKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      handleAddHashtag();
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!image) {
      toast.error('Please select an image');
      return;
    }

    createPostMutation.mutate({
      image,
      caption: caption.trim() || undefined,
      hashtags: hashtags.length > 0 ? hashtags : undefined,
    });
  };

  return (
    <div className="mt-16 max-w-2xl mx-auto">
      <div className="bg-white border border-gray-200 rounded-lg">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <button
            onClick={() => navigate(-1)}
            className="p-2 hover:bg-gray-100 rounded-full"
          >
            <ArrowLeftIcon className="h-6 w-6" />
          </button>
          <h1 className="text-lg font-semibold">Create new post</h1>
          <button
            onClick={handleSubmit}
            disabled={!image || createPostMutation.isLoading}
            className="text-blue-500 font-semibold disabled:opacity-50"
          >
            {createPostMutation.isLoading ? <LoadingSpinner size="sm" /> : 'Share'}
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6">
          {/* Image Upload */}
          {!previewUrl ? (
            <div
              onClick={() => fileInputRef.current?.click()}
              className="border-2 border-dashed border-gray-300 rounded-lg p-12 text-center cursor-pointer hover:border-gray-400 transition-colors"
            >
              <PhotoIcon className="h-16 w-16 text-gray-400 mx-auto mb-4" />
              <p className="text-xl mb-2">Drag photos and videos here</p>
              <button
                type="button"
                className="px-4 py-2 bg-blue-500 text-white rounded-lg font-semibold hover:bg-blue-600 transition-colors"
              >
                Select from computer
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleImageSelect}
                className="hidden"
              />
            </div>
          ) : (
            <div className="space-y-4">
              {/* Image Preview */}
              <div className="relative aspect-square rounded-lg overflow-hidden">
                <img
                  src={previewUrl}
                  alt="Preview"
                  className="w-full h-full object-cover"
                />
                <button
                  type="button"
                  onClick={() => {
                    setImage(null);
                    setPreviewUrl(null);
                  }}
                  className="absolute top-2 right-2 p-2 bg-black bg-opacity-50 rounded-full hover:bg-opacity-70 transition-colors"
                >
                  <XMarkIcon className="h-5 w-5 text-white" />
                </button>
              </div>

              {/* Caption */}
              <div>
                <textarea
                  value={caption}
                  onChange={(e) => setCaption(e.target.value)}
                  placeholder="Write a caption..."
                  rows={4}
                  maxLength={2200}
                  className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:border-gray-400 resize-none"
                />
                <p className="text-xs text-gray-500 text-right mt-1">
                  {caption.length}/2,200
                </p>
              </div>

              {/* Hashtags */}
              <div>
                <label className="block text-sm font-semibold mb-2">
                  Add hashtags
                </label>
                <div className="flex flex-wrap gap-2 mb-2">
                  {hashtags.map((tag) => (
                    <span
                      key={tag}
                      className="inline-flex items-center gap-1 px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm"
                    >
                      #{tag}
                      <button
                        type="button"
                        onClick={() => handleRemoveHashtag(tag)}
                        className="hover:text-blue-600"
                      >
                        <XMarkIcon className="h-4 w-4" />
                      </button>
                    </span>
                  ))}
                </div>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={hashtagInput}
                    onChange={(e) => setHashtagInput(e.target.value)}
                    onKeyDown={handleHashtagKeyDown}
                    placeholder="Type a hashtag and press Enter"
                    className="flex-1 px-4 py-2 border border-gray-200 rounded-lg focus:outline-none focus:border-gray-400"
                  />
                  <button
                    type="button"
                    onClick={handleAddHashtag}
                    className="px-4 py-2 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
                  >
                    Add
                  </button>
                </div>
              </div>

              {/* Submit Button - Mobile */}
              <button
                type="submit"
                disabled={createPostMutation.isLoading}
                className="w-full py-3 gradient-btn text-white font-semibold rounded-lg disabled:opacity-50 md:hidden"
              >
                {createPostMutation.isLoading ? (
                  <LoadingSpinner size="sm" />
                ) : (
                  'Share'
                )}
              </button>
            </div>
          )}
        </form>
      </div>
    </div>
  );
};

export default CreatePost;
