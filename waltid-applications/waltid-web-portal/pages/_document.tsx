import {Head, Html, Main, NextScript} from "next/document";
import Footer from "@/components/sections/Footer";

export default function Document() {
  return (
    <Html lang="en" className="bg-gray-50">
      <Head />
      <body>
        <Main />
        <NextScript />
      </body>
      <Footer />
    </Html>
  );
}
