/**
 * Visual feedback for "Add to cart": clones the product image and flies it to the navbar cart icon
 * (`[data-cart-target]`). Purely cosmetic — skipped when the user prefers reduced motion or the target is hidden.
 */
export function flyToCart(source: HTMLElement | null) {
  if (!source || typeof window === 'undefined') return;
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
  const target = document.querySelector<HTMLElement>('[data-cart-target]');
  if (!target || typeof source.animate !== 'function') return;

  const from = source.getBoundingClientRect();
  const to = target.getBoundingClientRect();
  if (from.width === 0 || to.width === 0) return;

  const size = Math.min(from.width, from.height, 96);
  const startX = from.left + from.width / 2 - size / 2;
  const startY = from.top + from.height / 2 - size / 2;
  const dx = to.left + to.width / 2 - (startX + size / 2);
  const dy = to.top + to.height / 2 - (startY + size / 2);

  const ghost = source.cloneNode(true) as HTMLElement;
  ghost.setAttribute('aria-hidden', 'true');
  Object.assign(ghost.style, {
    position: 'fixed', left: `${startX}px`, top: `${startY}px`, width: `${size}px`, height: `${size}px`,
    margin: '0', objectFit: 'cover', borderRadius: '9999px', zIndex: '60', pointerEvents: 'none',
    boxShadow: '0 10px 30px rgba(11,107,58,.35)',
  });
  document.body.appendChild(ghost);

  const flight = ghost.animate([
    { transform: 'translate(0, 0) scale(1)', opacity: 1 },
    { transform: `translate(${dx * 0.5}px, ${dy * 0.5 - 80}px) scale(.6)`, opacity: 0.95, offset: 0.55 },
    { transform: `translate(${dx}px, ${dy}px) scale(.15)`, opacity: 0.4 },
  ], { duration: 700, easing: 'cubic-bezier(.45,.05,.55,.95)' });

  const cleanup = () => {
    ghost.remove();
    target.animate?.([
      { transform: 'scale(1)' }, { transform: 'scale(1.25)' }, { transform: 'scale(.92)' }, { transform: 'scale(1)' },
    ], { duration: 380, easing: 'ease-out' });
  };
  flight.onfinish = cleanup;
  flight.oncancel = () => ghost.remove();
}
