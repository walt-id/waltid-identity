import {Fragment, ReactNode} from "react";
import {Dialog, Transition} from "@headlessui/react";
import {ChevronLeftIcon, XMarkIcon} from "@heroicons/react/24/outline";
import HSpacer from "@/components/walt/spacer/HSpacer";
import WaltIcon from "@/components/walt/logo/WaltIcon";

type ModalProps = {
  show: boolean;
  onClose?: () => void;
  children: ReactNode;
  showBack?: boolean;
  onBackPress?: () => void;
  securedByWalt?: boolean;
  showClose?: boolean;
};

const Modal = ({
  show,
  onClose = () => {},
  children,
  showBack,
  onBackPress,
  securedByWalt = true,
  showClose = true,
}: ModalProps) => {
  return (
    <Transition.Root show={show} as={Fragment}>
      <Dialog as="div" className="relative z-20" onClose={onClose}>
        <Transition.Child
          as={Fragment}
          enter="ease-out duration-300"
          enterFrom="opacity-0"
          enterTo="opacity-100"
          leave="ease-in duration-200"
          leaveFrom="opacity-100"
          leaveTo="opacity-0"
        >
          <div className="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity" />
        </Transition.Child>

        <div className="fixed inset-0 z-10 overflow-y-auto">
          <div className="flex min-h-full items-center justify-center p-4 text-center sm:items-center sm:p-0">
            <Transition.Child
              as={Fragment}
              enter="ease-out duration-300"
              enterFrom="opacity-0 translate-y-4 sm:translate-y-0 sm:scale-95"
              enterTo="opacity-100 translate-y-0 sm:scale-100"
              leave="ease-in duration-200"
              leaveFrom="opacity-100 translate-y-0 sm:scale-100"
              leaveTo="opacity-0 translate-y-4 sm:translate-y-0 sm:scale-95"
            >
              <Dialog.Panel className="relative transform overflow-hidden rounded-lg bg-white px-4 pt-5 pb-4 text-left shadow-xl transition-all sm:my-8 sm:w-full sm:max-w-lg sm:p-6">
                <div className="flex flex-row justify-between items-center">
                  {showBack === true ? (
                    <ChevronLeftIcon
                      height={25}
                      className="text-gray-500 cursor-pointer"
                      onClick={onBackPress}
                    />
                  ) : (
                    <div></div>
                  )}

                  {showClose && (
                    <div className="bg-gray-100 rounded-full p-1 cursor-pointer">
                      <XMarkIcon
                        height={25}
                        className="text-gray-500"
                        onClick={onClose}
                      />
                    </div>
                  )}
                </div>
                <div className="h-3"></div>
                <div>{children}</div>
                <HSpacer size={4} />
                {securedByWalt && (
                  <div className="flex flex-col items-center">
                    <div className="flex flex-row gap-2 items-center content-center text-sm text-center text-gray-500">
                      <p className="">Secured by walt.xyz</p>
                      <WaltIcon height={15} width={15} type="gray" />
                    </div>
                  </div>
                )}
              </Dialog.Panel>
            </Transition.Child>
          </div>
        </div>
      </Dialog>
    </Transition.Root>
  );
};

export default Modal;
