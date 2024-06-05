type Props = {
  value: string;
  onChange: (value: string) => void;
  type: string;
  name: string;
  label: string;
  placeholder: string;
  error?: boolean;
  errorText?: string;
  disabled?: boolean;
  showLabel?: boolean;
};

export default function InputField({
  value,
  onChange,
  type,
  label,
  placeholder,
  name,
  error = false,
  errorText,
  disabled = false,
  showLabel = false,
}: Props) {
  return (
    <div>
      <label
        htmlFor="email"
        className={`${!showLabel ? 'sr-only' : 'text-sm text-gray-800'}`}
      >
        {label}
      </label>
      <input
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
        type={type}
        name={name}
        id={name}
        className={`${
          !error
            ? 'border-gray-300 focus:border-blue-500 focus:ring-blue-500'
            : 'border-red-400'
        } border block w-full rounded-md shadow-sm  sm:text-sm py-1 px-1 mt-1`}
        placeholder={placeholder}
        value={value}
        aria-invalid={error}
        aria-describedby="email-error"
      />
      {error && (
        <p className="mt-2 text-sm text-red-600" id="email-error">
          {errorText}
        </p>
      )}
    </div>
  );
}
