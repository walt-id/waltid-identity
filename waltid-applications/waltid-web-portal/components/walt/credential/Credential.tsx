'use client';

import WaltIcon from "@/components/walt/logo/WaltIcon";

type Props = {
  id: string;
  title: string;
  description?: string;
  selected?: boolean;
  onClick: (id: string) => void;
};

export default function Credential({
  id,
  title,
  description,
  selected = false,
  onClick,
}: Props) {
  const blueDark = 'bg-gradient-to-r from-primary-400 to-primary-600 z-[-2]';
  const blueLight = 'bg-gradient-to-r from-primary-700 to-primary-900 z-[-2]';

  return (
    <div onClick={() => onClick(id)}>
      <div
        className={`${
          selected ? 'drop-shadow-2xl' : 'drop-shadow-sm'
        } flex flex-col
         rounded-xl py-7 px-8 text-gray-100 h-[225px] w-[360px] cursor-pointer overflow-hidden ${
           selected ? blueLight : blueDark
         }`}
      >
        <div className="flex flex-row">
          {selected ? (
            <WaltIcon height={35} width={35} outline type="white" />
          ) : (
            <WaltIcon height={35} width={35} outline type="white" />
          )}
        </div>
        <div className="mb-8 mt-12">
          <h6 className={'text-2xl font-bold '}>
            {title.length > 20 ? title.substring(0, 20) + '...' : title}
          </h6>
          <span>{description}</span>
        </div>
      </div>
    </div>
  );
}
