import { NavLink, useNavigate } from 'react-router-dom';
import {
  HomeIcon,
  MagnifyingGlassIcon,
  PlusCircleIcon,
  BellIcon,
  UserIcon,
  ArrowRightOnRectangleIcon,
} from '@heroicons/react/24/outline';
import {
  HomeIcon as HomeIconSolid,
  MagnifyingGlassIcon as MagnifyingGlassIconSolid,
  PlusCircleIcon as PlusCircleIconSolid,
  BellIcon as BellIconSolid,
  UserIcon as UserIconSolid,
} from '@heroicons/react/24/solid';
import { useAuthStore } from '../hooks/useAuthStore';
import { authService } from '../services/authService';

const Sidebar: React.FC = () => {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await authService.logout();
    } finally {
      logout();
      navigate('/login');
    }
  };

  const navItems = [
    { to: '/', icon: HomeIcon, activeIcon: HomeIconSolid, label: 'Home' },
    { to: '/search', icon: MagnifyingGlassIcon, activeIcon: MagnifyingGlassIconSolid, label: 'Search' },
    { to: '/create', icon: PlusCircleIcon, activeIcon: PlusCircleIconSolid, label: 'Create' },
    { to: '/notifications', icon: BellIcon, activeIcon: BellIconSolid, label: 'Notifications' },
    { to: `/profile/${user?.username}`, icon: UserIcon, activeIcon: UserIconSolid, label: 'Profile' },
  ];

  return (
    <aside className="hidden md:flex fixed left-0 top-16 bottom-0 w-64 bg-white border-r border-gray-200 flex-col p-4">
      <nav className="flex-1 space-y-1">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              `flex items-center px-4 py-3 rounded-lg transition-colors ${
                isActive
                  ? 'bg-gray-100 font-semibold'
                  : 'hover:bg-gray-50'
              }`
            }
          >
            {({ isActive }) => (
              <>
                {isActive ? (
                  <item.activeIcon className="h-6 w-6 mr-4" />
                ) : (
                  <item.icon className="h-6 w-6 mr-4" />
                )}
                <span>{item.label}</span>
              </>
            )}
          </NavLink>
        ))}
      </nav>

      <button
        onClick={handleLogout}
        className="flex items-center px-4 py-3 rounded-lg hover:bg-gray-50 text-gray-700 transition-colors"
      >
        <ArrowRightOnRectangleIcon className="h-6 w-6 mr-4" />
        <span>Logout</span>
      </button>
    </aside>
  );
};

export default Sidebar;
