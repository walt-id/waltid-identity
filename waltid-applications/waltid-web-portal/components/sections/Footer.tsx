export default function Footer() {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="mt-24">
      <div className="my-8 md:order-1 md:mt-0">
        <p className="text-center text-xs leading-5 text-gray-500">
          &copy; {currentYear} walt.id GmbH. All rights reserved.
        </p>
      </div>
    </footer>
  );
}
