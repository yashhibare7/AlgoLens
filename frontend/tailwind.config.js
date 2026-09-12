/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        mono: ['JetBrains Mono', 'Fira Code', 'Consolas', 'ui-monospace', 'monospace'],
      },
      colors: {
        // One place to retune the visualizer palette. Each maps to an ElementState
        // from the backend trace contract.
        cell: {
          default: '#1e293b',
          active: '#0ea5e9',
          comparing: '#f59e0b',
          written: '#8b5cf6',
          swapping: '#f43f5e',
          sorted: '#10b981',
          found: '#10b981',
          pivot: '#d946ef',
          excluded: '#334155',
        },
      },
      keyframes: {
        'flash-in': {
          '0%': { transform: 'scale(0.9)', opacity: '0.4' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
      },
      animation: {
        'flash-in': 'flash-in 180ms ease-out',
      },
    },
  },
  plugins: [],
};
