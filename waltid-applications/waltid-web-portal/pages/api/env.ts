import type {NextApiRequest, NextApiResponse} from "next";

type ResponseData = {};

export default function handler(
  req: NextApiRequest,
  res: NextApiResponse<ResponseData>
) {
  res.status(200).json({
    NEXT_PUBLIC_VC_REPO: process.env.NEXT_PUBLIC_VC_REPO,
    NEXT_PUBLIC_ISSUER: process.env.NEXT_PUBLIC_ISSUER,
    NEXT_PUBLIC_VERIFIER: process.env.NEXT_PUBLIC_VERIFIER,
    NEXT_PUBLIC_WALLET: process.env.NEXT_PUBLIC_WALLET,
  });
}
