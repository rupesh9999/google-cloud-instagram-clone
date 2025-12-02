import { Link } from 'react-router-dom';
import { MagnifyingGlassIcon, PlusCircleIcon, BellIcon } from '@heroicons/react/24/outline';
import { useAuthStore } from '../hooks/useAuthStore';

const Navbar: React.FC = () => {
  const { user } = useAuthStore();

  return (
    <nav className="fixed top-0 left-0 right-0 h-16 bg-white border-b border-gray-200 z-50">
      <div className="max-w-6xl mx-auto px-4 h-full flex items-center justify-between">
        {/* Logo */}
        <Link to="/" className="flex items-center">
          <span className="text-2xl font-bold gradient-text">Instagram</span>
        </Link>

        {/* Search Bar - Desktop */}
        <div className="hidden md:flex items-center">
          <div className="relative">
            <MagnifyingGlassIcon className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400" />
            <input
              type="text"
              placeholder="Search"
              className="w-64 pl-10 pr-4 py-2 bg-gray-100 rounded-lg border-none focus:outline-none focus:ring-2 focus:ring-pink-500"
            />
          </div>
        </div>

        {/* Right Icons */}
        <div className="flex items-center space-x-4">
          <Link to="/search" className="md:hidden p-2 hover:bg-gray-100 rounded-full">
            <MagnifyingGlassIcon className="h-6 w-6 text-gray-700" />
          </Link>
          <Link to="/create" className="p-2 hover:bg-gray-100 rounded-full">
            <PlusCircleIcon className="h-6 w-6 text-gray-700" />
          </Link>
          <Link to="/notifications" className="p-2 hover:bg-gray-100 rounded-full relative">
            <BellIcon className="h-6 w-6 text-gray-700" />
            {/* Notification badge would go here */}
          </Link>
          <Link to={`/profile/${user?.username}`} className="flex items-center">
            <img
              src={user?.profilePictureUrl || `https://ui-avatars.com/api/?name=${user?.username}&background=random`}
              alt={user?.username}
              className="h-8 w-8 rounded-full object-cover border border-gray-200"
            />
          </Link>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
