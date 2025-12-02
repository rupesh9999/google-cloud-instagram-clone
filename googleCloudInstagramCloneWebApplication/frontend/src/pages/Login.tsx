import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../hooks/useAuthStore';
import { authService } from '../services/authService';
import toast from 'react-hot-toast';
import LoadingSpinner from '../components/LoadingSpinner';

const Login: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();
  const { setAuth } = useAuthStore();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!username || !password) {
      toast.error('Please fill in all fields');
      return;
    }

    setIsLoading(true);
    try {
      const response = await authService.login({ username, password });
      setAuth(response.token, response.user);
      toast.success('Welcome back!');
      navigate('/');
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Invalid credentials';
      toast.error(message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm">
        {/* Logo Card */}
        <div className="bg-white border border-gray-200 rounded-lg p-10 mb-4">
          <h1 className="text-4xl font-bold text-center gradient-text mb-8">Instagram</h1>
          
          <form onSubmit={handleSubmit} className="space-y-4">
            <input
              type="text"
              placeholder="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-md text-sm focus:outline-none focus:border-gray-400"
              disabled={isLoading}
            />
            <input
              type="password"
              placeholder="Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-md text-sm focus:outline-none focus:border-gray-400"
              disabled={isLoading}
            />
            <button
              type="submit"
              disabled={isLoading || !username || !password}
              className="w-full py-3 gradient-btn text-white font-semibold rounded-lg disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isLoading ? <LoadingSpinner size="sm" /> : 'Log In'}
            </button>
          </form>

          <div className="flex items-center my-6">
            <div className="flex-1 h-px bg-gray-200" />
            <span className="px-4 text-sm text-gray-500 font-semibold">OR</span>
            <div className="flex-1 h-px bg-gray-200" />
          </div>

          <button className="w-full py-2 text-blue-900 font-semibold text-sm hover:text-blue-700">
            Log in with Google
          </button>

          <p className="text-center mt-4 text-sm text-blue-900 cursor-pointer hover:underline">
            Forgot password?
          </p>
        </div>

        {/* Sign Up Card */}
        <div className="bg-white border border-gray-200 rounded-lg p-6 text-center">
          <p className="text-sm">
            Don't have an account?{' '}
            <Link to="/register" className="text-blue-500 font-semibold hover:text-blue-600">
              Sign up
            </Link>
          </p>
        </div>

        {/* App Download */}
        <div className="mt-6 text-center">
          <p className="text-sm text-gray-600 mb-4">Get the app.</p>
          <div className="flex justify-center space-x-2">
            <img
              src="https://static.cdninstagram.com/rsrc.php/v3/yz/r/c5Rp7Ym-Klz.png"
              alt="Download on App Store"
              className="h-10"
            />
            <img
              src="https://static.cdninstagram.com/rsrc.php/v3/yu/r/EHY6QnZYdNX.png"
              alt="Get it on Google Play"
              className="h-10"
            />
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
