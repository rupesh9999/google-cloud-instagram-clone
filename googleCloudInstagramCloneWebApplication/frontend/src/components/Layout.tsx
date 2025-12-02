import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import Sidebar from './Sidebar';

const Layout: React.FC = () => {
  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <div className="flex">
        <Sidebar />
        <main className="flex-1 max-w-2xl mx-auto px-4 py-6 md:ml-64">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default Layout;
