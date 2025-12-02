import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../hooks/useAuthStore';
import { authService } from '../services/authService';
import toast from 'react-hot-toast';
import LoadingSpinner from '../components/LoadingSpinner';

const Register: React.FC = () => {
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();
  const { setAuth } = useAuthStore();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!email || !fullName || !username || !password) {
      toast.error('Please fill in all fields');
      return;
    }

    if (password.length < 6) {
      toast.error('Password must be at least 6 characters');
      return;
    }

    setIsLoading(true);
    try {
      const response = await authService.register({ email, fullName, username, password });
      setAuth(response.token, response.user);
      toast.success('Account created successfully!');
      navigate('/');
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Registration failed';
      toast.error(message);
    } finally {
      setIsLoading(false);
    }
  };

  const isFormValid = email && fullName && username && password.length >= 6;

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4 py-8">
      <div className="w-full max-w-sm">
        {/* Registration Card */}
        <div className="bg-white border border-gray-200 rounded-lg p-10 mb-4">
          <h1 className="text-4xl font-bold text-center gradient-text mb-4">Instagram</h1>
          <p className="text-center text-gray-500 font-semibold mb-6">
            Sign up to see photos and videos from your friends.
          </p>
          
          <button className="w-full py-2 gradient-btn text-white font-semibold rounded-lg mb-4">
            Log in with Google
          </button>

          <div className="flex items-center my-4">
            <div className="flex-1 h-px bg-gray-200" />
            <span className="px-4 text-sm text-gray-500 font-semibold">OR</span>
            <div className="flex-1 h-px bg-gray-200" />
          </div>

          <form onSubmit={handleSubmit} className="space-y-3">
            <input
              type="email"
              placeholder="Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-md text-sm focus:outline-none focus:border-gray-400"
              disabled={isLoading}
            />
            <input
              type="text"
              placeholder="Full Name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-md text-sm focus:outline-none focus:border-gray-400"
              disabled={isLoading}
            />
            <input
              type="text"
              placeholder="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, ''))}
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
            
            <p className="text-xs text-center text-gray-500 py-2">
              People who use our service may have uploaded your contact information to Instagram.{' '}
              <a href="#" className="text-blue-900">Learn More</a>
            </p>
            
            <p className="text-xs text-center text-gray-500 pb-2">
              By signing up, you agree to our{' '}
              <a href="#" className="text-blue-900">Terms</a>,{' '}
              <a href="#" className="text-blue-900">Privacy Policy</a> and{' '}
              <a href="#" className="text-blue-900">Cookies Policy</a>.
            </p>

            <button
              type="submit"
              disabled={isLoading || !isFormValid}
              className="w-full py-3 gradient-btn text-white font-semibold rounded-lg disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isLoading ? <LoadingSpinner size="sm" /> : 'Sign Up'}
            </button>
          </form>
        </div>

        {/* Log In Card */}
        <div className="bg-white border border-gray-200 rounded-lg p-6 text-center">
          <p className="text-sm">
            Have an account?{' '}
            <Link to="/login" className="text-blue-500 font-semibold hover:text-blue-600">
              Log in
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

export default Register;
