/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      colors: {
        bordeaux: { DEFAULT:'#8B3A3A', light:'#a84848', pale:'#f9f0f0', pale2:'#f0d9d9' },
        teal:     { DEFAULT:'#4A7C7E', light:'#5a9496', pale:'#edf4f4' },
        taupe:    { DEFAULT:'#9B8B6E' },
        cream:    { DEFAULT:'#FAFAF7', 2:'#F5F3EF', 3:'#EDE8DF', 4:'#E2DBD0' },
        ink:      { DEFAULT:'#1E1A16', 2:'#3D362E', 3:'#6B5F52', 4:'#9E9082', 5:'#C8BEB2' },
      },
      fontFamily: {
        display: ['"Cormorant Garamond"', 'serif'],
        body:    ['"DM Sans"', 'sans-serif'],
      },
      animation: {
        'fade-up':   'fadeUp .7s ease forwards',
        'float':     'floatA 7s ease-in-out infinite',
        'ticker':    'ticker 42s linear infinite',
        'spin-slow': 'spinSlow 22s linear infinite',
        'slide-down':'slideDown .3s ease forwards',
      },
      keyframes: {
        fadeUp:    { from:{opacity:'0',transform:'translateY(26px)'}, to:{opacity:'1',transform:'translateY(0)'} },
        floatA:    { '0%,100%':{transform:'translateY(0)'}, '50%':{transform:'translateY(-16px)'} },
        ticker:    { '0%':{transform:'translateX(0)'}, '100%':{transform:'translateX(-50%)'} },
        spinSlow:  { from:{transform:'rotate(0deg)'}, to:{transform:'rotate(360deg)'} },
        slideDown: { from:{opacity:'0',transform:'translateY(-8px)'}, to:{opacity:'1',transform:'translateY(0)'} },
      },
      boxShadow: {
        'soft':    '0 2px 12px rgba(30,26,22,0.05)',
        'medium':  '0 8px 32px rgba(30,26,22,0.09)',
        'large':   '0 20px 60px rgba(30,26,22,0.12)',
        'bord':    '0 4px 20px rgba(139,58,58,0.28)',
        'bord-lg': '0 8px 36px rgba(139,58,58,0.38)',
        'teal':    '0 4px 20px rgba(74,124,126,0.28)',
      },
    },
  },
  plugins: [],
}