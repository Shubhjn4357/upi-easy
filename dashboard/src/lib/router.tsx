/* eslint-disable react/only-export-components */
// Lightweight Declarative React Router with Security Guards
import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { hasRole } from './auth';

interface RouterContextType {
  path: string;
  navigate: (path: string) => void;
}

const RouterContext = createContext<RouterContextType>({
  path: '/overview',
  navigate: () => {},
});

export function Router({ children }: { children: ReactNode }) {
  const getInitialPath = () => {
    const hash = window.location.hash.replace(/^#\/?/, '/');
    return hash && hash !== '/' ? hash : '/overview';
  };

  const [path, setPath] = useState(getInitialPath);

  useEffect(() => {
    const handleHashChange = () => {
      const newPath = window.location.hash.replace(/^#\/?/, '/');
      setPath(newPath && newPath !== '/' ? newPath : '/overview');
    };
    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, []);

  const navigate = (newPath: string) => {
    const normalized = newPath.startsWith('/') ? newPath : `/${newPath}`;
    window.location.hash = `#${normalized}`;
    setPath(normalized);
  };

  return (
    <RouterContext.Provider value={{ path, navigate }}>
      {children}
    </RouterContext.Provider>
  );
}

export function useNavigate() {
  const context = useContext(RouterContext);
  return context.navigate;
}

export function useLocation() {
  const context = useContext(RouterContext);
  return { pathname: context.path };
}

const ParamsContext = createContext<Record<string, string>>({});

export function useParams<T extends Record<string, string> = Record<string, string>>(): T {
  return useContext(ParamsContext) as T;
}

function matchPath(pattern: string, pathname: string): { matched: boolean; params: Record<string, string> } {
  if (pattern === pathname) return { matched: true, params: {} };
  if (pattern === '*') return { matched: true, params: {} };

  const patternParts = pattern.split('/').filter(Boolean);
  const pathParts = pathname.split('?')[0].split('/').filter(Boolean);

  if (patternParts.length !== pathParts.length) {
    return { matched: false, params: {} };
  }

  const params: Record<string, string> = {};
  for (let i = 0; i < patternParts.length; i++) {
    const p = patternParts[i];
    const val = pathParts[i];
    if (p.startsWith(':')) {
      params[p.slice(1)] = decodeURIComponent(val);
    } else if (p !== val) {
      return { matched: false, params: {} };
    }
  }

  return { matched: true, params };
}

export function Routes({ children }: { children: ReactNode }) {
  const { path } = useContext(RouterContext);
  let matchedElement: ReactNode = null;
  let matchedParams: Record<string, string> = {};

  React.Children.forEach(children, (child) => {
    if (!React.isValidElement(child)) return;
    const props = child.props as { path: string; element: ReactNode };
    if (!matchedElement) {
      const match = matchPath(props.path, path);
      if (match.matched) {
        matchedElement = props.element;
        matchedParams = match.params;
      }
    }
  });

  return (
    <ParamsContext.Provider value={matchedParams}>
      {matchedElement}
    </ParamsContext.Provider>
  );
}

export function Route({ path: _path, element }: { path: string; element: ReactNode }) {
  return <>{element}</>;
}

export function ProtectedRoute({
  isAuthenticated,
  userRole,
  requiredRole,
  children,
}: {
  isAuthenticated: boolean;
  userRole?: string;
  requiredRole?: string;
  fallback?: ReactNode;
  children: ReactNode;
}) {
  if (!isAuthenticated) {
    return (
      <div className="p-8 text-center text-muted-foreground">
        <p className="font-semibold text-sm text-foreground">Authentication Required</p>
        <p className="text-xs mt-1">Please sign in to access this section.</p>
      </div>
    );
  }

  if (requiredRole && !hasRole(userRole, requiredRole)) {
    return (
      <div className="p-8 text-center text-muted-foreground">
        <p className="font-semibold text-sm text-destructive">Access Restricted</p>
        <p className="text-xs mt-1">
          Your role ({userRole || 'MEMBER'}) lacks permission for this view. Requires {requiredRole}.
        </p>
      </div>
    );
  }

  return <>{children}</>;
}

export function Link({
  to,
  className = '',
  children,
  ...props
}: {
  to: string;
  className?: string;
  children: ReactNode;
  [key: string]: unknown;
}) {
  const navigate = useNavigate();
  return (
    <a
      href={`#${to.startsWith('/') ? to : `/${to}`}`}
      onClick={(e) => {
        e.preventDefault();
        navigate(to);
      }}
      className={className}
      {...(props as React.AnchorHTMLAttributes<HTMLAnchorElement>)}
    >
      {children}
    </a>
  );
}
