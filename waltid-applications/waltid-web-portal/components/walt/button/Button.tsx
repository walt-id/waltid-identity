import {ReactNode} from "react";
import {AiOutlineLoading3Quarters} from "react-icons/ai";

type ButtonSize = 'sm' | 'md' | 'lg' | 'xl';

type ButtonProps = {
  size?: ButtonSize;
  children: ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  loading?: boolean;
  type?: 'button' | 'submit' | 'reset';
  loadingText?: string;
  style?: 'button' | 'link';
  color?: 'primary' | 'secondary' | 'green';
  className?: string;
};

export default function Button({
  size,
  children,
  onClick,
  disabled = false,
  loading = false,
  type = 'button',
  loadingText = 'Loading',
  style = 'button',
  color = 'primary',
  className = '',
}: ButtonProps) {
  const buttonSize = size || 'md';
  let baseClasses =
    'inline-flex items-center text-sm leading-4 font-medium rounded-full focus:outline-none';

  // Button Primary Style Classes
  const backgroundClassesButtonPrimaryStyle =
    'bg-primary-400 hover:bg-primary-700 disabled:bg-gray-200 shadow-sm';
  const textClassesButtonPrimaryStyle =
    'text-primary-50 disabled:text-gray-400';

  // Button Secondary Style Classes
  const backgroundClassesButtonSecondaryStyle =
    'bg-gray-200 hover:bg-gray-300 disabled:bg-gray-200 shadow-sm';
  const textClassesButtonSecondaryStyle =
    'text-gray-500 disabled:text-gray-400';

  // Button Green Style Classes
  const backgroundClassesButtonGreenStyle =
    'bg-green-700 hover:bg-green-700 disabled:bg-green-200 shadow-sm';
  const textClassesButtonGreenStyle = 'text-green-50 disabled:text-green-400';

  // Link Secondary Style Classes
  const textClassesLinkSecondaryStyle =
    'text-gray-500 underline hover:text-primary-400';
  const backgroundClassesLinkSecondaryStyle = 'bg-transparent';

  // Link primary Style Classes
  const textClassesLinkPrimaryStyle =
    'text-primary-400 underline hover:text-primary-700';
  const backgroundClassesLinkPrimaryStyle = 'bg-transparent';

  // Button Size Classes
  const buttonSizeClasses = {
    sm: 'px-5 py-1.5',
    md: 'px-8 py-2.5',
    lg: 'px-10 py-3.5',
    xl: 'px-12 py-4',
  };

  let finalClasses = `${baseClasses}`;

  if (style === 'button') {
    if (color === 'primary') {
      finalClasses =
        finalClasses +
        ' ' +
        backgroundClassesButtonPrimaryStyle +
        ' ' +
        textClassesButtonPrimaryStyle;
    } else if (color === 'secondary') {
      finalClasses =
        finalClasses +
        ' ' +
        backgroundClassesButtonSecondaryStyle +
        ' ' +
        textClassesButtonSecondaryStyle;
    } else if (color === 'green') {
      finalClasses =
        finalClasses +
        ' ' +
        backgroundClassesButtonGreenStyle +
        ' ' +
        textClassesButtonGreenStyle;
    }
  }

  if (style === 'link') {
    if (color === 'primary') {
      finalClasses =
        finalClasses +
        ' ' +
        backgroundClassesLinkPrimaryStyle +
        ' ' +
        textClassesLinkPrimaryStyle;
    } else if (color === 'secondary') {
      finalClasses =
        finalClasses +
        ' ' +
        backgroundClassesLinkSecondaryStyle +
        ' ' +
        textClassesLinkSecondaryStyle;
    }
  }

  finalClasses = className + finalClasses + ' ' + buttonSizeClasses[buttonSize];

  return (
    <button
      disabled={disabled}
      onClick={onClick}
      type={type}
      className={finalClasses}
    >
      {loading ? (
        <div className="flex flex-row gap-2 items-center">
          <AiOutlineLoading3Quarters className="animate-spin" />
          <p>{loadingText}</p>
        </div>
      ) : (
        <div> {children} </div>
      )}
    </button>
  );
}
